package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class ComplexityOfNeedsApiMockServer : WireMockServer(8098) {
  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubSuccess(
    prisonerNumber: String,
    level: String = "medium",
  ) {
    stubFor(
      get(urlPathMatching("/v1/complexity-of-need/offender-no/$prisonerNumber"))
        .willReturn(
          okJson(
            """
  {
    "offenderNo": "$prisonerNumber",
    "level": "$level",
    "sourceUser": "JSMITH_GEN",
    "sourceSystem": "hmpps-api-client-id",
    "notes": "string",
    "createdTimeStamp": "2021-03-02T17:18:46.457Z",
    "updatedTimeStamp": "2021-03-02T17:18:46.457Z",
    "active": true
  }
            """.trimIndent(),
          ),
        ),
    )
  }
}

class ComplexityOfNeedsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val complexityOfNeedsApi = ComplexityOfNeedsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = complexityOfNeedsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = complexityOfNeedsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = complexityOfNeedsApi.stop()
}
