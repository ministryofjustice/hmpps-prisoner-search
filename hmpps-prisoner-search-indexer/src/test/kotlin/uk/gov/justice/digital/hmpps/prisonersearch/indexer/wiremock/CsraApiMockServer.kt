package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class CsraMockServer : WireMockServer(8100) {
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

  fun stubCurrentRatingSummary(
    prisonerNumber: String,
    rating: String = "HIGH_GENERAL",
    shortDescription: String = "High – general",
  ) {
    stubFor(
      get(urlEqualTo("/csra-review/prisoner/$prisonerNumber/current-rating-summary")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "prisonerNumber": "$prisonerNumber",
              "status": "COMPLETE",
              "rating": "$rating",
              "ratingStage": "FINAL",
              "ratingDate": "2026-11-03",
              "ratingDescription": "High risk – general",
              "ratingShortDescription": "$shortDescription",
              "reviewDueDate": "2027-05-03"
            }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }
}

class CsraApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val csraApi = CsraMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = csraApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = csraApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = csraApi.stop()
}
