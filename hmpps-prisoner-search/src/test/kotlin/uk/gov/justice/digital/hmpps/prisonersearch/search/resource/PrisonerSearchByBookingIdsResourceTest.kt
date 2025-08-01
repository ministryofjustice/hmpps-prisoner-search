package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.toPrisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.BookingIds

class PrisonerSearchByBookingIdsResourceTest : AbstractSearchIntegrationTest() {
  override fun loadPrisonerData() {
    List(12) { i -> "AN$i" }.mapIndexed { bookingId: Int, prisonNumber: String ->
      PrisonerBuilder(bookingId = bookingId.toLong(), prisonerNumber = prisonNumber).toPrisoner()
    }.apply { loadPrisoners(this) }
  }

  @Test
  fun `booking ids search returns bad request when no ids provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue("""{ "bookingIds":[] }"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage").value<String> {
        assertThat(it).contains("size must be between 1 and 1000").contains("must not be empty")
      }
  }

  @Test
  fun `booking ids search returns bad request when over 1000 prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/booking-ids")
      .body(BodyInserters.fromValue(gson.toJson(BookingIds((1..1001L).toList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage").value<String> {
        assertThat(it).contains("size must be between 1 and 1000")
      }
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

  @Test
  fun `returns only requested response fields`() {
    webTestClient.post()
      .uri {
        it.path("/prisoner-search/booking-ids")
          .queryParam("responseFields", "prisonerNumber")
          .queryParam("responseFields", "lastName")
          .build()
      }
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(1L, 2L)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(2)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN1")
      .jsonPath("$[0].lastName").isEqualTo("MORALES")
      .jsonPath("$[0].firstName").doesNotExist()
      .jsonPath("$[1].prisonerNumber").isEqualTo("AN2")
      .jsonPath("$[1].lastName").isEqualTo("MORALES")
      .jsonPath("$[1].firstName").doesNotExist()
  }

  @Test
  fun `returns bad request for invalid response field`() {
    webTestClient.post()
      .uri {
        it.path("/prisoner-search/booking-ids")
          .queryParam("responseFields", "prisonerNumber")
          .queryParam("responseFields", "doesNotExist")
          .build()
      }
      .body(BodyInserters.fromValue(gson.toJson(BookingIds(listOf(1L, 2L)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("userMessage").value<String> {
        assertThat(it).contains("Invalid response fields requested: [doesNotExist]")
      }
  }
}
