package uk.gov.justice.digital.hmpps.prisonersearch.indexer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ClasspathFileSource
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class RestrictedPatientApiMockServer : WireMockServer(8095, ClasspathFileSource("restricted-patients"), false) {
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

  fun verifyGetRestrictedPatientRequest(prisonerNumber: String) {
    verify(
      WireMock.getRequestedFor(WireMock.urlEqualTo("/restricted-patient/prison-number/$prisonerNumber")),
    )
  }
}

class RestrictedPatientsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val restrictedPatientsApi = RestrictedPatientApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = restrictedPatientsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = restrictedPatientsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = restrictedPatientsApi.stop()
}
