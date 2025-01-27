package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.ClasspathFileSource
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class RestrictedPatientApiMockServer : WireMockServer(8095, ClasspathFileSource("restricted-patients"), false) {
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

  fun stubGetRestrictedPatient(prisonerNumber: String) {
    stubFor(
      get(urlPathMatching("/restricted-patient/prison-number/$prisonerNumber"))
        .willReturn(
          okJson(
            """
            {
              "id": 1000001,
              "prisonerNumber": "$prisonerNumber",
              "supportingPrison": {"agencyId": "LEI", "agencyType":  "INST", "active":  true},
              "hospitalLocation": {"agencyId": "HOS1", "agencyType":  "HOSP", "active":  true},
              "dischargeTime": "2024-11-10T12:00:00"
            }
            """.trimIndent(),
          ),
        ),
    )
  }

  fun verifyGetRestrictedPatientRequest(prisonerNumber: String) {
    verify(
      getRequestedFor(urlEqualTo("/restricted-patient/prison-number/$prisonerNumber")),
    )
  }
}

class RestrictedPatientsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val restrictedPatientsApi = RestrictedPatientApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = restrictedPatientsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = restrictedPatientsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = restrictedPatientsApi.stop()
}
