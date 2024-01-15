package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.toPrisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.BookingIds

class PrisonerSearchByBookingIdsResourceTest : AbstractSearchDataIntegrationTest() {

  override fun loadPrisonerData() {
    val prisonerNumbers = List(12) { i -> "AN$i" }
    var prisoners = ArrayList<Prisoner>()
    prisonerNumbers.forEachIndexed { bookingId: Int, prisonNumber: String ->
      val offenderBooking = getOffenderBooking(prisonNumber, bookingId.toLong())
      val prisoner = toPrisoner(offenderBooking, null, null)
      prisoners.add(prisoner)
    }
    loadPrisoners(prisoners)
  }

  @Test
  fun `booking ids search returns bad request when no ids provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue("""{ "bookingIds":[] }"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 BookingIds")
  }

  @Test
  fun `booking ids search returns bad request when over 1000 prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds((1..1001L).toList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 BookingIds")
  }

  @Test
  fun `booking ids search returns offender records, single result`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue("""{"bookingIds":[2]}"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
  }

  @Test
  fun `ids search returns offender records, ignoring not found ids`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(2L, 300L, 400L)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].bookingId").isEqualTo(2)
  }

  @Test
  fun `ids search can return over 10 hits (default max hits is 10)`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds((0..12L).toList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(12)
  }

  @Test
  fun `access forbidden for ids search when no role`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(1)))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  private fun getOffenderBooking(offenderNo: String, bookingId: Long): OffenderBooking {
    val templateOffender = gson.fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)
    return templateOffender.copy(offenderNo = offenderNo, bookingId = bookingId)
  }
}

private fun String.readResourceAsText() =
  PrisonerSearchByBookingIdsResourceTest::class.java.getResource(this).readText()
