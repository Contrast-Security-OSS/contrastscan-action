package com.contrastsecurity.scan

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.marcinziolo.kotlin.wiremock.equalTo
import com.marcinziolo.kotlin.wiremock.post
import com.marcinziolo.kotlin.wiremock.returnsJson
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempfile
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.inspectors.forAll
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beInstanceOf
import kotlin.text.Charsets.UTF_8


class GitHubCodeScanningAnalysisSpecs : BehaviorSpec({
    // We need the wiremock server to be fresh/reset for each "spec test" rather than globally
    // in its default SingleInstance mode.  A "spec test" is each "then-clause" in our spec description
    isolationMode = IsolationMode.InstancePerLeaf

    val wiremock = WireMockServer(options().notifier(ConsoleNotifier(true)).dynamicPort())
    listener(WireMockListener(wiremock, ListenerMode.PER_SPEC))
    val GITHUB_REPO = "myowner/myrepo"
    val ENDPOINT = "/${GITHUB_REPO}/code-scanning/sarifs"

    val defaultSarif = tempfile()
    val os = defaultSarif.writer(UTF_8)
    os.write(
        """
                    {
                      "version" : "2.1.0",
                      "${"$"}schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                      "runs" : [ {
                        "tool" : {
                          "driver" : {
                            "name" : "Contrast Scan",
                            "organization" : "Contrast Security, Inc.",
                            "version" : "pkg: 2.0.0-SNAPSHOT, engine: 2.0.0-SNAPSHOT, policy: 2.0.0-SNAPSHOT",
                            "informationUri" : "https://www.contrastsecurity.com"
                          }
                        },
                        "artifacts" : [ ],
                        "results" : []
                      } ]
                    }
                """.trimIndent()
    )
    os.flush()
    os.close()
    val default_gzip_b64_val_of_sarif =
        "H4sIAAAAAAAAAGWQQU+EMBCF7/srmsYjtOseuRkv7kVN0JPhUGuBKrRkZpCsG/67pXQj6qmZr9P3Xt95xxj/NIDWO84Kxg/iWux5tuAr1K3pVcQt0YCFlKAm0Vhqx9cRDWjvyDgS2vfSK7SYk0aJCmyd42C07BWSAVlGIVKXqzjm0Uq8Y3COfjA6XMxe2DmMAZD33QLWMYA3sCHrFgXoVG9ixtuQBoIhK7VaJdOGh0Y5+6Xo8smfTaNHsHTK2NFpsX2z7WT4aAp2CFn3eXl/81jePTxlzLjGOvOfD76z+vSXb6Wtqz30Mc0z2F/1TtMkdAqHKdvSLk+v53jOqxhXQLZWmlJrVcJgcOwSrAKaWbWbvwGBx3gM6QEAAA=="



    given("Successful 202 GitHub Response") {
        val defaultScmData = GitHubScmData(
            GITHUB_REPO,
            "ref/branch",
            "sha1234",
            "token1234",
            wiremock.baseUrl(),
        )
        wiremock.post {
            url equalTo "$ENDPOINT"
        } returnsJson {
            statusCode = 202
            body = """
                {
                    "id":"79e01b1c-46f3-11ec-866c-cba55b5482c9",
                    "url":"${wiremock.baseUrl()}/$ENDPOINT/79e01b1c-46f3-11ec-866c-cba55b5482c9"
                    }
                """.trimIndent()
        }

        val scanAnalysis = GitHubCodeScanningAnalysis(defaultScmData)

        and("completed sarif") {
            val completedSarif = defaultSarif
            `when`("uploadSarif is called") {
                val uploadstatus = scanAnalysis.uploadSarif(completedSarif.toPath())

                then("it should return Success") {
                    wiremock.verify(
                        postRequestedFor(urlEqualTo("$ENDPOINT"))
                            .withHeader("Authorization", equalTo("Bearer ${defaultScmData.token}"))
                            .withRequestBody(
                                equalToJson(
                                    """
                            {
                            "ref":"${defaultScmData.ref}",
                            "commit_sha":"${defaultScmData.sha}",
                            "checkout_uri": "file:///github/workspace",
                            "tool_name" : "Contrast Scan",
                            "sarif": "${default_gzip_b64_val_of_sarif}"
                            }
                            """
                                )
                            )
                    )

                    uploadstatus.isSuccess shouldBe true
                    uploadstatus.getOrNull() shouldNotBe null
                }
            }
        }
        and("sarif missing tool name") {
            val sarif = tempfile()
            val os = sarif.writer(UTF_8)
            os.write(
                """
                    {
                      "version" : "2.1.0",
                      "${"$"}schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                      "runs" : [ {
                        "tool" : {
                          "driver" : {
                            "organization" : "Contrast Security, Inc.",
                            "version" : "pkg: 2.0.0-SNAPSHOT, engine: 2.0.0-SNAPSHOT, policy: 2.0.0-SNAPSHOT",
                            "informationUri" : "https://www.contrastsecurity.com"
                          }
                        },
                        "artifacts" : [ ],
                        "results" : []
                      } ]
                    }
                """.trimIndent()
            )
            os.flush()
            val expected_gzip_b64_val_of_sarif =
                "H4sIAAAAAAAAAGWQQU+EMBCF7/srGuIR2nWP3IwXvagJejIcai0wCi2ZGSTrhv9u6XYT1FPzvmbmvXmnnRDZl0UC7zJRiuwgr+U+y1d8Raazg464Yx6pVAr1LFvgbnqbyKLxjq1jafygvCaggg0p0ghNQaM1atDEFlUVF7G+fEVZRCv5QcE5+uHkaDV7FacgA2Dv+xWcZQDvCCHrFgXosdUOvjVfTrgNqTAYi8qaCYGPubh3RkaTNLO9ePxsS3EISfZF9XDzVN09PufCuhac/c9H34M5/uXb1eAaj0NM84Lwq7x5nqVJ4ShlW7vL0vQS3+W8LNPI0GjDqZM6YbQ09QnWAS2i3i0/HA/8jscBAAA="

            `when`("uploadSarif is called") {
                val uploadstatus = scanAnalysis.uploadSarif(sarif.toPath())

                then("it should fill in tool name as \"Contrast Scan\"") {
                    wiremock.verify(
                        postRequestedFor(urlEqualTo("$ENDPOINT"))
                            .withHeader("Authorization", equalTo("Bearer ${defaultScmData.token}"))
                            .withRequestBody(
                                equalToJson(
                                    """
                            {
                            "ref":"${defaultScmData.ref}",
                            "commit_sha":"${defaultScmData.sha}",
                            "checkout_uri": "file:///github/workspace",
                            "tool_name" : "Contrast Scan",
                            "sarif": "${expected_gzip_b64_val_of_sarif}"
                            }
                            """
                                )
                            )
                    )
                    and("upload status is success") {
                        uploadstatus.isSuccess shouldBe true
                        uploadstatus.getOrNull() shouldNotBe null
                    }
                }
            }
        }
    }

    listOf(
        200 to Result.failure(RuntimeException()),
        201 to Result.failure(RuntimeException()),
        202 to Result.success(""),
        404 to Result.failure(RuntimeException()),
        430 to Result.failure(RuntimeException()),
    ).forAll {
        given("GitHub API Response statusCode [${it.first}]") {

            wiremock.post {
                url equalTo "$ENDPOINT"
            } returnsJson {
                statusCode = it.first
            }
            val defaultScmData = GitHubScmData(
                GITHUB_REPO,
                "ref/branch",
                "sha1234",
                "token1234",
                wiremock.baseUrl(),
            )
            val scanAnalysis = GitHubCodeScanningAnalysis(defaultScmData)

            `when`("uploadSarif is called") {
                val uploadstatus = scanAnalysis.uploadSarif(defaultSarif.toPath())
                then("it should return Failure") {
                    wiremock.verify(
                        postRequestedFor(urlEqualTo("$ENDPOINT"))
                            .withHeader("Authorization", equalTo("Bearer ${defaultScmData.token}"))
                            .withRequestBody(
                                equalToJson(
                                    """
                            {
                            "ref":"${defaultScmData.ref}",
                            "commit_sha":"${defaultScmData.sha}",
                            "checkout_uri": "file:///github/workspace",
                            "tool_name" : "Contrast Scan",
                            "sarif": "${default_gzip_b64_val_of_sarif}"
                            }
                            """
                                )
                            )
                    )

                    uploadstatus.onFailure { _ ->
                        it.second.isFailure shouldBe true
                        it.second.exceptionOrNull() should beInstanceOf<RuntimeException>()
                    }
                    uploadstatus.onSuccess { _ ->
                        it.second.isSuccess shouldBe true
                        it.second.getOrNull() should beInstanceOf<String>()
                    }
                }
            }
        }
    }

    given("results over github rejection limit") {
        val defaultScmData = GitHubScmData(
            GITHUB_REPO,
            "ref/branch",
            "sha1234",
            "token1234",
            wiremock.baseUrl(),
        )
        val scanAnalysis = GitHubCodeScanningAnalysis(defaultScmData)
        val defaultSarif = tempfile()
        val os = defaultSarif.writer(UTF_8)
        os.write(
            """
                    {
                      "version" : "2.1.0",
                      "${"$"}schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                      "runs" : [ {
                        "tool" : {
                          "driver" : {
                            "name" : "Contrast Scan",
                            "organization" : "Contrast Security, Inc.",
                            "version" : "pkg: 2.0.0-SNAPSHOT, engine: 2.0.0-SNAPSHOT, policy: 2.0.0-SNAPSHOT",
                            "informationUri" : "https://www.contrastsecurity.com"
                          }
                        },
                        "artifacts" : [ ],
                        "results" : [ ${"{},".repeat(25000)}{} ]
                      } ]
                    }
                """.trimIndent()
        )
        os.flush()

        `when`("uploadSarif is called") {
            val uploadstatus = scanAnalysis.uploadSarif(defaultSarif.toPath())

            then("it should return Failure") {
                uploadstatus.isFailure shouldBe true
                uploadstatus.exceptionOrNull() should beInstanceOf<IllegalArgumentException>()
            }

        }
    }
    given("results over github truncation limit") {
        val defaultScmData = GitHubScmData(
            GITHUB_REPO,
            "ref/branch",
            "sha1234",
            "token1234",
            wiremock.baseUrl(),
        )
        val scanAnalysis = GitHubCodeScanningAnalysis(defaultScmData)
        val defaultSarif = tempfile()
        val os = defaultSarif.writer(UTF_8)
        os.write(
            """
                    {
                      "version" : "2.1.0",
                      "${"$"}schema" : "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
                      "runs" : [ {
                        "tool" : {
                          "driver" : {
                            "name" : "Contrast Scan",
                            "organization" : "Contrast Security, Inc.",
                            "version" : "pkg: 2.0.0-SNAPSHOT, engine: 2.0.0-SNAPSHOT, policy: 2.0.0-SNAPSHOT",
                            "informationUri" : "https://www.contrastsecurity.com"
                          }
                        },
                        "artifacts" : [ ],
                        "results" : [ ${"{},".repeat(5000)}{} ]
                      } ]
                    }
                """.trimIndent()
        )
        os.flush()

        wiremock.post {
            url equalTo "$ENDPOINT"
        } returnsJson {
            statusCode = 202
            body = """
                {
                    "id":"79e01b1c-46f3-11ec-866c-cba55b5482c9",
                    "url":"${wiremock.baseUrl()}/$ENDPOINT/79e01b1c-46f3-11ec-866c-cba55b5482c9"
                    }
                """.trimIndent()
        }

        `when`("uploadSarif is called") {
            val uploadstatus = scanAnalysis.uploadSarif(defaultSarif.toPath())

            then("it should return Success") {
                uploadstatus.isSuccess shouldBe true
                uploadstatus.getOrNull() should beInstanceOf<String>()
            }

        }
    }
})