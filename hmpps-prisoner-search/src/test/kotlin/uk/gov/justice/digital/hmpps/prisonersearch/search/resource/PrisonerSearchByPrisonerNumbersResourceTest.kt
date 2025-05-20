package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.PrisonerNumbers

class PrisonerSearchByPrisonerNumbersResourceTest : AbstractSearchIntegrationTest() {
  override fun loadPrisonerData() {
    val prisonerData = getTestPrisonerNumbers(12).map { PrisonerBuilder(prisonerNumber = it) }
    loadPrisonersFromBuilders(prisonerData)
  }

  private fun getTestPrisonerNumbers(count: Int): List<String> = List(count) { i -> "AN$i" }

  @Test
  fun `prisoner number search returns bad request when no prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue("{\"prisonerNumbers\":[]}"))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 PrisonerNumbers")
  }

  @Test
  fun `prisoner number search returns bad request when over 1000 prison numbers provided`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(getTestPrisonerNumbers(1001)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search  - please provide a minimum of 1 and a maximum of 1000 PrisonerNumbers")
  }

  @Test
  fun `prisoner number search returns offender records, single result`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue("""{"prisonerNumbers":["AN2"]}"""))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
      .jsonPath("$[0].imprisonmentStatus").isEqualTo("LIFE")
      .jsonPath("$[0].imprisonmentStatusDescription").isEqualTo("Life imprisonment")
      .jsonPath("$[0].convictedStatus").isEqualTo("Remand")
  }

  @Test
  fun `prisoner number search returns offender records, ignoring not found prison numbers`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(listOf("AN2", "AN33", "AN44")))))
      .headers(setAuthorisation(roles = listOf("PRISONER_SEARCH__PRISONER__RO")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("AN2")
  }

  @Test
  fun `prisoner number search can return over 10 hits (default max hits is 10)`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(getTestPrisonerNumbers(12)))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(12)
  }

  @Test
  fun `access forbidden for prison number search when no role`() {
    webTestClient.post().uri("/prisoner-search/prisoner-numbers")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(arrayListOf("ABC")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `returns only requested response fields`() {
    webTestClient.post()
      .uri {
        it.path("/prisoner-search/prisoner-numbers")
          .queryParam("responseFields", "prisonerNumber")
          .queryParam("responseFields", "lastName")
          .build()
      }
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(listOf("AN1", "AN2")))))
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
        it.path("/prisoner-search/prisoner-numbers")
          .queryParam("responseFields", "prisonerNumber")
          .queryParam("responseFields", "doesNotExist")
          .build()
      }
      .body(BodyInserters.fromValue(gson.toJson(PrisonerNumbers(listOf("AN1", "AN2")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("userMessage").value<String> {
        assertThat(it).contains("Invalid response fields requested: [doesNotExist]")
      }
  }
}
