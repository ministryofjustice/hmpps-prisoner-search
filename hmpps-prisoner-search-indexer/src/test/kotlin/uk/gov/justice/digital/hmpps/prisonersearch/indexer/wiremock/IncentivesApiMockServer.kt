package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario
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

  fun stubNotFound() {
    stubFor(
      get(urlPathMatching("/incentive-reviews/booking/\\d+"))
        .willReturn(
          aResponse()
            .withStatus(404),
        ),
    )
  }

  fun stubErrorFollowedByNotFound() {
    stubFor(
      get(urlPathMatching("/incentive-reviews/booking/\\d+"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(503),
        ).willSetStateTo("404"),
    )
    stubFor(
      get(urlPathMatching("/incentive-reviews/booking/\\d+"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("404")
        .willReturn(
          aResponse()
            .withStatus(404),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubError() {
    stubFor(
      get(urlPathMatching("/incentive-reviews/booking/\\d+"))
        .willReturn(
          aResponse()
            .withStatus(503),
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
      get(urlPathMatching("/incentive-reviews/booking/\\d+"))
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

  fun verifyGetCurrentIncentiveRequest(bookingId: Long, count: Int = 1) {
    verify(
      count,
      getRequestedFor(urlEqualTo("/incentive-reviews/booking/$bookingId?with-details=false")),
    )
  }
}

class IncentivesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesApi = IncentivesMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = incentivesApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = incentivesApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = incentivesApi.stop()
}
