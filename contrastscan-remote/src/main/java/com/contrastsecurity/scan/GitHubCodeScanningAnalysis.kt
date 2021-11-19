package com.contrastsecurity.scan

import com.contrastsecurity.sarif.SarifSchema210
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.io.path.absolutePathString
import kotlin.text.Charsets.UTF_8

data class GitHubScmData(
    val repository: String,
    val ref: String,
    val sha: String,
    val token: String,
    val apiBaseUrl: String,
)

class GitHubCodeScanningAnalysis(val gitHubScmData: GitHubScmData) {
    private val repoName = gitHubScmData.repository
    private val authorization = "Bearer ${gitHubScmData.token}"
    private val baseUrl = "${gitHubScmData.apiBaseUrl}/$repoName"
    private val codeScanningEndpoint = "$baseUrl/code-scanning/sarifs"


    fun uploadSarif(sarif: Path): Result<String> {
        logger.info("Uploading sarif results: ${sarif.absolutePathString()} for repo $repoName")
        logger.debug("GitHub SCM info: $gitHubScmData")

        // TODO: validateSarif(sarif)

        val sarifNodes = objectMapper.readValue(sarif.toFile(), SarifSchema210::class.java)
        if (sarifNodes.runs.get(0).results.size > githubResultRejectionLimit) {
            return Result.failure(
                IllegalArgumentException("Sarif result limit hit ${sarifNodes.runs.get(0).results.size} > $githubResultRejectionLimit. GitHub will reject this analysis")
            )
        } else if (sarifNodes.runs.get(0).results.size > githubResultTruncationLimit) {
            logger.error("Sarif truncation limit hit ${sarifNodes.runs.get(0).results.size} > $githubResultTruncationLimit results. Some results will be truncated")
        }
        val toolName = sarifNodes.runs.get(0).tool.driver.name ?: "Contrast Scan"

        val inputStream = FileInputStream(sarif.toFile())
        val sarifString = inputStream.readAllBytes().decodeToString()
        val outputBytes = ByteArrayOutputStream()
        GZIPOutputStream(
            Base64.getEncoder().wrap(outputBytes)
        ).bufferedWriter(UTF_8).use { it.write(sarifString) }
        val b64Sarif = outputBytes.toByteArray().toString(UTF_8)

        logger.debug("encoded sarif data size is ${b64Sarif.length} bytes")

        if (b64Sarif.length > githubUploadSizeLimit) {
            return Result.failure(
                IllegalArgumentException("Encoded sarif is ${b64Sarif.length} bytes. GitHub will reject an upload over $githubUploadSizeLimit")
            )
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(codeScanningEndpoint))
            .setHeader("Authorization", authorization)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "ref" to gitHubScmData.ref,
                            "commit_sha" to gitHubScmData.sha,
                            "sarif" to b64Sarif,
                            "checkout_uri" to "file:///github/workspace",
                            "tool_name" to toolName,
                        )
                    )
                )
            )
            .build()
        var result = kotlin.runCatching {
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        }.recover {
            return Result.failure(it)
        }

        val response = result.getOrNull()!!
        logger.debug("response body: ${response.body()}")

        when (response.statusCode()) {
            202 -> return Result.success(response.body())
            else -> return Result.failure(RuntimeException("GitHub API call failed with http status code ${response.statusCode()} and body: ${response.body()}"))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)
        private val objectMapper = ObjectMapper()

        const val githubUploadSizeLimit = 10 * 1024 * 1024 // 10MB
        const val githubResultTruncationLimit = 5000
        const val githubResultRejectionLimit = 25000
    }

}