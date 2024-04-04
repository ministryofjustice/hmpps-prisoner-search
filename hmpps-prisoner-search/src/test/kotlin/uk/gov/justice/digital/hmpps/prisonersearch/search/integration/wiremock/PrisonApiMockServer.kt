package uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class PrisonApiMockServer : WireMockServer(8093) {
  fun stubHealthPing(status: Int) {
    stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubAlertTypes(response: String) {
    stubFor(
      WireMock.get("/api/reference-domains/domains/ALERT/codes").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(response),
      ),
    )
  }

  fun stubAlertCodes(response: String) {
    stubFor(
      WireMock.get("/api/reference-domains/domains/ALERT_CODE/codes").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(response),
      ),
    )
  }
}

class PrisonApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = prisonApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = prisonApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = prisonApi.stop()
}
