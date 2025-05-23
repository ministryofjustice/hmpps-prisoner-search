package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PossibleMatchCriteria
import java.time.LocalDate

class PossibleMatchesSearchResourceTest : AbstractSearchDataIntegrationTest() {
  @Test
  fun `search for possible matches access forbidden when no authority`() {
    webTestClient.post().uri("/prisoner-search/possible-matches")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `search for possible matches access forbidden when no role`() {
    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(PossibleMatchCriteria(null, null, null, null, "A1234AB"))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `search for possible matches bad request when no criteria provided`() {
    webTestClient.post().uri("/prisoner-search/possible-matches")
      .body(BodyInserters.fromValue(gson.toJson(PossibleMatchCriteria(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can search for possible matches by noms number - get one result`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, null, "A7089FA"),
      "/results/possibleMatches/search_results_A7089FA.json",
    )
  }

  @Test
  fun `can search for possible matches by noms number - get no results`() {
    possibleMatch(PossibleMatchCriteria(null, null, null, null, "A6759ZZ"), "/results/possibleMatches/empty.json")
  }

  @Test
  fun `can search for possible matches by noms number - when case insensitive`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, null, "a7089fa"),
      "/results/possibleMatches/search_results_A7089FA.json",
    )
  }

  @Test
  fun `can search for possible matches by pnc number - long year`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "2015/001234S", null),
      "/results/possibleMatches/search_results_pnc.json",
    )
  }

  @Test
  fun `can search for possible matches by pnc number - long year without leading zeros`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "2015/1234S", null),
      "/results/possibleMatches/search_results_pnc.json",
    )
  }

  @Test
  fun `can perform a match on PNC number short year 19 century`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "89/4444S", null),
      "/results/possibleMatches/search_results_pnc2.json",
    )
  }

  @Test
  fun `can perform a match on PNC number long year 19 century`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "1989/4444S", null),
      "/results/possibleMatches/search_results_pnc2.json",
    )
  }

  @Test
  fun `can perform a match on PNC number long year 19 century extra zeros`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "1989/0004444S", null),
      "/results/possibleMatches/search_results_pnc2.json",
    )
  }

  @Test
  fun `can search for possible matches by pnc number - when case insensitive`() {
    possibleMatch(
      PossibleMatchCriteria(null, null, null, "2015/001234s", null),
      "/results/possibleMatches/search_results_pnc.json",
    )
  }

  @Test
  fun `can search for possible matches by last name and date of birth - get one result`() {
    possibleMatch(
      (PossibleMatchCriteria(null, "Davies", LocalDate.of(1990, 1, 31), null, null)),
      "/results/possibleMatches/search_results_A7089FA.json",
    )
  }

  @Test
  fun `can search for possible matches by last name and date of birth - get no results`() {
    possibleMatch(
      (PossibleMatchCriteria(null, "Smith", LocalDate.of(1990, 1, 31), null, null)),
      "/results/possibleMatches/empty.json",
    )
  }

  @Test
  fun `can search for possible matches with all search criteria - get one result`() {
    possibleMatch(
      (PossibleMatchCriteria("Paul", "Booth", LocalDate.of(1976, 3, 1), "2015/001234S", "A9999AA")),
      "/results/possibleMatches/multiple_criteria_single_match.json",
    )
  }

  @Test
  fun `can search for possible matches with all search criteria - get multiple results`() {
    possibleMatch(
      (PossibleMatchCriteria("James", "Davies", LocalDate.of(1990, 1, 31), "2015/001234S", "A7089FB")),
      "/results/possibleMatches/multiple_results.json",
    )
  }

  @Test
  fun `should return bad request for invalid response fields`() {
    val search = PossibleMatchCriteria(null, null, null, null, "A7089FA")
    webTestClient.possibleMatch(search, listOf("prisonerNumber", "doesNotExist"))
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("developerMessage").isEqualTo("Invalid response fields requested: [doesNotExist]")
  }

  @Test
  fun `should only return requested response fields - by noms number`() {
    val search = PossibleMatchCriteria(null, null, null, null, "A7089FA")
    webTestClient.possibleMatch(search, listOf("prisonerNumber", "lastName"))
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("A7089FA")
      .jsonPath("$[0].lastName").isEqualTo("DAVIES")
      .jsonPath("$[0].firstName").doesNotExist()
  }

  @Test
  fun `should only return requested response fields - by pnc number`() {
    val search = PossibleMatchCriteria(null, null, null, "2015/001234S", null)
    webTestClient.possibleMatch(search, listOf("prisonerNumber", "lastName"))
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("A9999AA")
      .jsonPath("$[0].lastName").isEqualTo("BOOTH")
      .jsonPath("$[0].firstName").doesNotExist()
  }

  @Test
  fun `should only return requested response fields - by last name and date of birth`() {
    val search = PossibleMatchCriteria(null, "Davies", LocalDate.of(1990, 1, 31), null, null)
    webTestClient.possibleMatch(search, listOf("prisonerNumber", "lastName"))
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.length()").isEqualTo(1)
      .jsonPath("$[0].prisonerNumber").isEqualTo("A7089FA")
      .jsonPath("$[0].lastName").isEqualTo("DAVIES")
      .jsonPath("$[0].firstName").doesNotExist()
  }

  private fun WebTestClient.possibleMatch(search: PossibleMatchCriteria, responseFields: List<String>) = post()
    .uri {
      it.path("/prisoner-search/possible-matches")
        .queryParam("responseFields", responseFields)
        .build()
    }
    .body(BodyInserters.fromValue(gson.toJson(search)))
    .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
    .header("Content-Type", "application/json")
    .exchange()
}
