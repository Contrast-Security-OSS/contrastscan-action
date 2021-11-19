package com.contrastsecurity.scan

import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "contrastscan-remote",
    mixinStandardHelpOptions = true,
    description = ["Perform contrastscan in a github action"]
)
class GitHubActionCli : Callable<Int> {
    @CommandLine.Option(
        names = ["-d", "--src-dir"],
        defaultValue = ".",
        description = ["where to find the source tree of the project"]
    )
    private lateinit var sourceRoot: File

    @CommandLine.Option(
        names = ["-m", "--prescan-out"],
        defaultValue = "../prescan.json",
        description = ["where to write the result (\${DEFAULT-VALUE})"]
    )
    private lateinit var prescanOutPath: File

    @CommandLine.Option(
        names = ["-s", "--sarif-out"],
        defaultValue = "../results.sarif",
        description = ["where to write the result (\${DEFAULT-VALUE})"]
    )
    private lateinit var sarifOutPath: File

    @CommandLine.Option(
        names = ["-u", "--url"],
        // TODO: change this to public site
        defaultValue = "https://teamserver-harmony-dev.contsec.com/Contrast",
        description = ["Contrast Platform API url (\${DEFAULT-VALUE})"]
    )
    private lateinit var apiUrl: String

    @CommandLine.Parameters(index = "0")
    private lateinit var artifactPath: Path


    @Throws(Exception::class)
    override fun call(): Int {
        val creds = collectContrastPlatformCreds()
        val gitHubScmData = collectGitHubScmData()
        val projectName = gitHubScmData.repository
        val labelName = "${gitHubScmData.ref}-${gitHubScmData.sha}"

        PreScanDataGenerator.generate(sourceRoot.toPath(), prescanOutPath.toPath())
        val sdk = ContrastRemoteScanner.connectToContrast(creds, URL(apiUrl))
        val scanner = ContrastRemoteScanner(creds.orgId, projectName, labelName, sdk)

        val proj = scanner.findOrCreateProject()
        val codeArtifact = scanner.upload(proj, prescanOutPath.toPath(), artifactPath)
        val scan = scanner.scan(proj, codeArtifact)
        logger.info("scan started: $scan")
        logger.info("Scan URL: ${scanner.createClickableScanURL(scan)}")

        scanner.waitForResults(scan, sarifOutPath.toPath())

        val codeScanner = GitHubCodeScanningAnalysis(gitHubScmData)
        codeScanner.uploadSarif(sarifOutPath.toPath())

        return 0
    }

    private fun collectContrastPlatformCreds(): ContrastPlatformCredentials {
        return ContrastPlatformCredentials(
            readEnvVar(USER_ENV),
            readEnvVar(ORG_ID_ENV),
            readEnvVar(API_KEY_ENV),
            readEnvVar(SERVICE_KEY_ENV)
        )
    }

    private fun collectGitHubScmData(): GitHubScmData {
        return GitHubScmData(
            readEnvVar(GH_REPO_ENV),
            readEnvVar(GH_REF_ENV),
            readEnvVar(GH_SHA_ENV),
            readEnvVar(GH_TOKEN_ENV),
            "https://api.github.com/repos",
        )
    }

    private fun readEnvVar(name: String): String {
        return System.getenv(name) ?: throw IllegalArgumentException("Environment var [$name] is not set!")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)
        const val USER_ENV = "CONTRAST__API__USER_NAME"
        const val API_KEY_ENV = "CONTRAST__API__API_KEY"
        const val SERVICE_KEY_ENV = "CONTRAST__API__SERVICE_KEY"
        const val ORG_ID_ENV = "CONTRAST__API__ORGANIZATION_ID"
        const val GH_REPO_ENV = "GITHUB_REPOSITORY"
        const val GH_SHA_ENV = "GITHUB_SHA"
        const val GH_TOKEN_ENV = "GITHUB_TOKEN"
        const val GH_REF_ENV = "GITHUB_REF"

        @JvmStatic
        fun main(args: Array<String>) {
            val exitCode = CommandLine(GitHubActionCli()).execute(*args)
            System.exit(exitCode)
        }
    }
}