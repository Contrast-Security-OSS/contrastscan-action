package com.contrastsecurity.scan

import com.contrastsecurity.sdk.ContrastSDK
import com.contrastsecurity.sdk.UserAgentProduct
import com.contrastsecurity.sdk.scan.CodeArtifact
import com.contrastsecurity.sdk.scan.Project
import com.contrastsecurity.sdk.scan.Scan
import com.contrastsecurity.sdk.scan.ScanSummary
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.UncheckedIOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.*
import java.util.function.Consumer
import kotlin.io.path.absolute

/**
 * A container to hold Contrast credential data.
 */
data class ContrastPlatformCredentials(
    val user: String,
    val orgId: String,
    val apikey: String,
    val serviceKey: String
)

class ContrastRemoteScanner(
    val orgId: String,
    val projectName: String,
    val labelName: String,
    private val sdk: ContrastSDK
) {

    fun upload(project: Project, prescan: Path, artifact: Path): CodeArtifact {
        require(!project.archived())
        logger.info("Uploading " + artifact.fileName + " to Contrast Scan")
        val codeArtifact = try {
            project.codeArtifacts().upload(artifact, prescan)
        } catch (e: Exception) {
            logger.error("Failed to upload code artifact to Contrast Scan")
            throw e
        }
        return codeArtifact
    }

    fun scan(project: Project, codeArtifact: CodeArtifact) : Scan {

        logger.info("Starting scan for project [${projectName}] with label [$labelName]")
        return try {
            project.scans().define().withLabel(labelName).withExistingCodeArtifact(codeArtifact).create()
        } catch (e: Exception) {
            logger.error("Failed to start scan for code artifact $codeArtifact")
            throw e
        }
    }

    fun findOrCreateProject(): Project {
        val projects = sdk.scan(orgId).projects()
        val maybeProject = projects.findByName(projectName)
        val project: Project =
            if (maybeProject.isEmpty) {
                logger.info("Creating project with name: '$projectName'")
                projects.define().withName(projectName).withLanguage("JAVA").create()
            } else {
                maybeProject.get()
            }

        return project
    }

    fun createClickableScanURL(scan: Scan): URL {
        val path = "/Contrast/static/ng/index.html#/${scan.organizationId()}/scans/${scan.projectId()}/scans/${scan.id()}"

        return try {
            val restURL = URL(sdk.restApiURL)
            URL(restURL.protocol, restURL.host, restURL.port, path)
        } catch (e: MalformedURLException) {
            logger.error("Error building clickable Scan URL. Please contact support@contrastsecurity.com for help")
            throw e
        }
    }

    fun waitForResults(scan: Scan, scanOutputPath: Path, timeoutMs: Long = 1000 * 60 * 10) {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val reportsDirectory: Path = scanOutputPath.absolute().normalize().parent
            try {
                logger.debug("creating reports dir: $reportsDirectory")
                Files.createDirectories(reportsDirectory)
            } catch (e: IOException) {
                logger.error("Failed to create Contrast Scan reports directory")
                throw e
            }
            val await = scan.await(scheduler)
            val save = await.thenCompose { completed: Scan ->
                CompletableFuture.runAsync(
                    {
                        try {
                            logger.debug("saving sarif results to ${scanOutputPath.absolute().normalize()}")
                            completed.saveSarif(scanOutputPath)
                        } catch (e: IOException) {
                            throw UncheckedIOException(e)
                        }
                    },
                    scheduler
                )
            }
            val output = await.thenAccept { completed: Scan ->
                val summary: ScanSummary
                summary = try {
                    completed.summary()
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
                writeSummaryToConsole(
                    summary,
                    { line: String? -> logger.debug(line) })
            }
            CompletableFuture.allOf(
                save.toCompletableFuture(),
                output.toCompletableFuture()
            )[timeoutMs, TimeUnit.MILLISECONDS]
        } catch (e: ExecutionException) {
            // try to unwrap the extraneous ExecutionException
            val cause = e.cause
            // ExecutionException should always have a cause, but its constructor does not enforce this,
            // so check if the cause is null
            val inner = cause ?: e
            throw RuntimeException("Failed to retrieve Contrast Scan results", inner)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error("Interrupted while retrieving Contrast Scan results")
            throw e
        } catch (e: TimeoutException) {
            val duration = Duration.ofMillis(timeoutMs)
            val durationString =
                if (duration.toMinutes() > 0) "${duration.toMinutes()} minutes" else "${(duration.toMillis() / 1000)} seconds"
            logger.error("Failed to retrieve Contrast Scan results in $durationString")
            throw e
        } finally {
            scheduler.shutdown()
        }
    }

    private fun writeSummaryToConsole(summary: ScanSummary, consoleLogger: Consumer<String?>) {
        consoleLogger.accept("Scan completed.")
        consoleLogger.accept("Summary from Contrast Teamserver:")
        consoleLogger.accept("New Results\t" + summary.totalNewResults())
        consoleLogger.accept("Fixed Results\t" + summary.totalFixedResults())
        consoleLogger.accept("Total Results\t" + summary.totalResults())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)

        fun connectToContrast(creds: ContrastPlatformCredentials, apiUrl: URL): ContrastSDK {

            return ContrastSDK.Builder(creds.user, creds.serviceKey, creds.apikey)
                .withApiUrl(apiUrl.toString())
                .withUserAgentProduct(userAgentString)
                .build()
        }

        val userAgentString = UserAgentProduct.of("Contrast Scan GitHub Action") //TODO: add version
    }
}