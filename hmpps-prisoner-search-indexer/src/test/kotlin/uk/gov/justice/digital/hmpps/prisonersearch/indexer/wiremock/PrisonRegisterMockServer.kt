package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonDto

class PrisonRegisterMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8099
  }

  fun stubHealthPing(status: Int) {
    val stat = if (status == 200) "UP" else "DOWN"
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
          .withBody("""{"status": "$stat"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetPrisons(
    returnResult: Boolean = true,
  ) {
    if (returnResult) {
      stubFor(
        get("/prisons")
          .willReturn(
            okJson(
              Gson().toJson(
                listOf(
                  PrisonDto(prisonId = "BZI", active = true, male = false, female = true),
                  PrisonDto(prisonId = "MDI", active = true, male = true, female = false),
                ),
              ),
            ),
          ),
      )
    } else {
      stubFor(get("/prisons").willReturn(status(404)))
    }
  }
}

class PrisonRegisterApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonRegisterApi = PrisonRegisterMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = prisonRegisterApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = prisonRegisterApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = prisonRegisterApi.stop()
}
