package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class NomisApiMockServer : WireMockServer(8094) {
  fun stubHealthPing(status: Int) {
    stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }
}

class NomisApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val nomisApi = NomisApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = nomisApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = nomisApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = nomisApi.stop()
}
