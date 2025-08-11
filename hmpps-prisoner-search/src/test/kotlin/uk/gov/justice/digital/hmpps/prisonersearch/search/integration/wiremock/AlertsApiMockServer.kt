package uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class AlertsApiMockServer : WireMockServer(8093) {
  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetAlertTypes(response: String) {
    stubFor(
      get("/alert-types?includeInactive=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(response),
      ),
    )
  }
}

class AlertsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val alertsApi = AlertsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = alertsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = alertsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = alertsApi.stop()
}
