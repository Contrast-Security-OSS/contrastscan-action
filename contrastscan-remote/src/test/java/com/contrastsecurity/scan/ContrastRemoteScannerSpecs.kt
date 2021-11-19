package com.contrastsecurity.scan

import com.contrastsecurity.sdk.ContrastSDK
import com.contrastsecurity.sdk.scan.*
import io.kotest.assertions.fail
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.FileNotFoundException
import java.net.URL
import java.net.UnknownHostException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletionStage


class ContrastRemoteScannerSpecs : BehaviorSpec({
    // We need the wiremock server to be fresh/reset for each "spec test" rather than globally
    // in its default SingleInstance mode.  A "spec test" is each "then-clause" in our spec description
    isolationMode = IsolationMode.InstancePerLeaf
    val APIURL = "https://contrastapi"
    val USER = "testuser"
    val ORG = "testOrg1234"
    val APIKEY = "testapikey1234"
    val SVCKEY = "testservicekey1234"
    val PROJNAME = "testproj"
    val LABELNAME = "testlabel"
    val DEFAULTCREDS = ContrastPlatformCredentials(USER, ORG, APIKEY, SVCKEY)
    val TIMEOUT = 10000L
    val codeArtifactPath = Path.of("target", "testCodeArtifact.jar")
    val prescanDataPath = Path.of("prescan.json")
    val scanOutputPath = tempfile()

    val mocksdk = mockk<ContrastSDK>()
    val mockProject = mockk<Project>()
    val mockProjects = mockk<Projects>()
    val mockDef = mockk<Project.Definition>()
    val mockCodeArtifact = mockk<CodeArtifact>()
    val mockCodeArtifacts = mockk<CodeArtifacts>()
    val mockScanMgr = mockk<ScanManager>()
    val SCANID = "scanId1234"
    val PROJECTID = "projectId1234"
    val mockScan = mockk<Scan>()
    val scanner = ContrastRemoteScanner(ORG, PROJNAME, LABELNAME, mocksdk)

    given("Contrast Platform SDK calls fail by Exception") {
        every { mocksdk.scan(ORG) } throws UnknownHostException()
        every {mockProject.archived()} returns false
        every { mockProject.scans() } throws UnknownHostException()
        every { mockProject.codeArtifacts() } returns mockCodeArtifacts
        every { mockCodeArtifacts.upload(codeArtifactPath, prescanDataPath) } throws FileNotFoundException()

        `when`("scan() is called") {
            val result = kotlin.runCatching {
                scanner.scan(mockProject, mockCodeArtifact)
            }

            then("UnknownHostException is thrown to caller") {
                result.isFailure shouldBe true
                result.exceptionOrNull() should beInstanceOf<UnknownHostException>()
            }
        }
        `when`("upload() is called") {
            val result = kotlin.runCatching {
                scanner.upload(mockProject, prescanDataPath, codeArtifactPath)
            }
            then("FileNotFoundException is thrown to caller") {
                result.isFailure shouldBe true
                result.exceptionOrNull() should beInstanceOf<FileNotFoundException>()
            }
        }
    }

    given("Contrast Platform has no existing Project") {

        every { mockDef.withName(PROJNAME) } returns mockDef
        every { mockDef.withLanguage("JAVA") } returns mockDef
        every { mockDef.create() } returns mockProject
        every { mockProjects.findByName(PROJNAME) } returns Optional.empty()
        every { mockScanMgr.projects() } returns mockProjects
        every { mockProjects.define() } returns mockDef
        every { mocksdk.scan(ORG) } returns mockScanMgr

        `when`("findOrCreateProject() is called") {
            val project = scanner.findOrCreateProject()

            then("a new project is created") {
                verify { mockDef.withName(PROJNAME) }
                verify { mockDef.withLanguage("JAVA") }
                verify { mockDef.create() }
            }
        }
    }
    given("Contrast Platform has an existing Project") {
        every { mockProjects.findByName(PROJNAME) } returns Optional.of(mockProject)
        every { mockScanMgr.projects() } returns mockProjects
        every { mocksdk.scan(ORG) } returns mockScanMgr

        and("Project is NOT archived") {
            every { mockProject.archived() } returns false
            every { mockProject.codeArtifacts() } returns mockCodeArtifacts
            every { mockCodeArtifacts.upload(codeArtifactPath, prescanDataPath)} returns mockCodeArtifact


            `when`("findOrCreateProject() is called") {
                val project = scanner.findOrCreateProject()

                then("the existing project is returned") {
                    project shouldBeSameInstanceAs mockProject
                }
            }

            `when`("upload() is called") {
                val result = kotlin.runCatching {
                    scanner.upload(mockProject, prescanDataPath, codeArtifactPath)
                }

                then("a valid CodeArtifact should be returned") {
                    result.isSuccess shouldBe true
                    result.getOrNull() shouldBeSameInstanceAs mockCodeArtifact
                    verify { mockCodeArtifacts.upload(codeArtifactPath, prescanDataPath) }
                }
            }
        }
        and("Project is archived") {
            every { mockProject.archived() } returns true

            `when`("findOrCreateProject() is called") {
                val project = scanner.findOrCreateProject()

                then("the existing project is returned") {
                    project shouldBeSameInstanceAs mockProject
                }
            }

            `when`("upload() is called") {
                val result = kotlin.runCatching {
                    scanner.upload(mockProject, Path.of("."), Path.of("."))
                }

                then("an IllegalArgumentException should be thrown") {
                    result.isFailure shouldBe true
                    result.exceptionOrNull() should beInstanceOf<IllegalArgumentException>()
                    verify { mockProject.archived() }
                }
            }
        }
    }

    given("A Scan has been created"){
        every { mockScan.id() } returns SCANID
        every { mockScan.projectId()} returns PROJECTID
        every { mockScan.organizationId()} returns ORG
        every { mocksdk.restApiURL } returns APIURL

        `when`("createClickableURL is called") {
            var url = scanner.createClickableScanURL(mockScan)

            then("it should return a fully qualified, valid URL") {
                println("url is $url")
                url shouldBe URL(URL(APIURL), "/Contrast/static/ng/index.html#/$ORG/scans/$PROJECTID/scans/$SCANID")
            }
        }

        and("the scan is finished") {
            `when`("waitForScan() is called"){
                xthen("then sarif output should be available"){
                    fail("not implemented")
                }
            }
        }

        and("the scan is still in process") {
            `when`("waitForScan() is called") {
                and("the scan finishes within the timeout period") {
                    xthen("the sarif output should be available") {
                        fail("not implemented")
                    }
                }
                and("when the timeout expires before the scan is finished"){
                    xthen("the sarif is not available") {
                        and("a TimeoutException is raised") {
                            fail("not implemented")
                        }
                    }
                }
            }
        }
    }
})