package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RootOffenderIdPage

class NomisApiMockServer : WireMockServer(8094) {
  fun stubHealthPing(status: Int) {
    stubFor(
      WireMock.get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubActiveOffenders(vararg prisoners: PrisonerBuilder) {
    val rootOffenderIds = prisoners.map { it.offenderId }
    stubFor(
      WireMock.get(urlPathEqualTo("/search/prisoners/id-ranges"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(Gson().toJson(listOf(RootOffenderIdPage(0, 1000)))),
        ),
    )
    stubFor(
      WireMock.get(urlPathEqualTo("/search/prisoners/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(Gson().toJson(rootOffenderIds)),
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
