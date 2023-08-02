package uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerNumberPage

class PrisonApiMockServer : WireMockServer(8093) {
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

  fun stubOffenders(vararg prisoners: PrisonerBuilder) {
    val prisonerNumbers = prisoners.map { it.prisonerNumber }
    stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/api/prisoners/prisoner-numbers"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(Gson().toJson(PrisonerNumberPage(prisonerNumbers, prisonerNumbers.size.toLong()))),
        ),
    )

    prisoners.forEach {
      stubGetOffender(it)
      stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/iep/reviews/booking/${it.bookingId}"))
          .willReturn(
            WireMock.aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(it.toIncentiveLevel()),
          ),
      )
    }
  }

  fun stubGetOffender(prisonerBuilder: PrisonerBuilder) {
    stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/offenders/${prisonerBuilder.prisonerNumber}"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(prisonerBuilder.toOffenderBooking()),
        ),
    )
  }

  fun stubGetNomsNumberForBooking(bookingId: Long, prisonerNumber: String) {
    stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/bookings/$bookingId?basicInfo=true"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(validBookingMessage(bookingId, prisonerNumber)),
        ),
    )
  }

  fun stubGetMergedIdentifiersByBookingId(bookingId: Long, prisonerNumber: String) {
    stubFor(
      WireMock.get(WireMock.urlEqualTo("/api/bookings/$bookingId/identifiers?type=MERGED"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                [
                  {
                    "type": "MERGED",
                    "value": "$prisonerNumber"
                  }
                ]
              """.trimIndent(),
            ),
        ),
    )
  }

  private fun validBookingMessage(bookingId: Long, prisonerNumber: String): String =
    """
    {
      "bookingId": $bookingId,
      "bookingNo": "V61587",
      "offenderNo": "$prisonerNumber",
      "firstName": "DAVID",
      "lastName": "WALLIS",
      "agencyId": "MDI",
      "assignedLivingUnitId": 721727,
      "activeFlag": true,
      "assignedLivingUnit": {
        "agencyId": "MDI",
        "locationId": 721726,
        "description": "A-1-003",
        "agencyName": "Moorland Prison"
      },
      "facialImageId": 1171172,
      "dateOfBirth": "1990-02-01"
    }
    """.trimIndent()
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
