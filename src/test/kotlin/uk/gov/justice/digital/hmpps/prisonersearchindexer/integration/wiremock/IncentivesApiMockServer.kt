package uk.gov.justice.digital.hmpps.prisonersearchindexer.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IncentivesMockServer : WireMockServer(8096) {
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

  fun stubCurrentIncentive(
    iepCode: String = "STD",
    iepLevel: String = "Standard",
    iepTime: String = "2022-11-10T15:47:24.682335",
    nextReviewDate: String = "2023-11-18",
  ) {
    stubFor(
      get(urlPathMatching("/iep/reviews/booking/\\d+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(
              """
              {
                  "id": 5850394,
                  "iepCode": "$iepCode",
                  "iepLevel": "$iepLevel",
                  "prisonerNumber": "A9412DY",
                  "bookingId": 1203242,
                  "iepDate": "2022-11-10",
                  "iepTime": "$iepTime",
                  "locationId": "RECP",
                  "iepDetails": [],
                  "nextReviewDate": "$nextReviewDate",
                  "daysSinceReview": 12
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun verifyGetCurrentIncentiveRequest(bookingId: Long) {
    verify(
      getRequestedFor(urlEqualTo("/iep/reviews/booking/$bookingId?with-details=false")),
    )
  }
}

class IncentivesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesApi = IncentivesMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = incentivesApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = incentivesApi.resetRequests()
  override fun afterAll(context: ExtensionContext): Unit = incentivesApi.stop()
}
