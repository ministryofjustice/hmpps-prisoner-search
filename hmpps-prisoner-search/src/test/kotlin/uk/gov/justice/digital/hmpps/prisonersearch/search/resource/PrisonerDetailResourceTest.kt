package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PrisonerDetailRequest
import java.time.LocalDate

class PrisonerDetailResourceTest : AbstractSearchDataIntegrationTest() {
  @Test
  fun `access forbidden when no authority`() {
    webTestClient.post().uri("/prisoner-detail")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = listOf("LEI", "MDI")))))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {
    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(KeywordRequest(orWords = "smith jones", prisonIds = emptyList()))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `should return bad request for invalid response fields`() {
    webTestClient.prisonerDetailSearch(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")), listOf("prisonerNumber", "doesNotExist"))
      .expectStatus().isBadRequest
      .expectBody().jsonPath("userMessage").value<String> {
        assertThat(it).contains("Invalid response fields requested: [doesNotExist]")
      }
  }

  @Test
  fun `should only return requested response fields`() {
    webTestClient.prisonerDetailSearch(PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")), listOf("prisonerNumber", "lastName"))
      .expectStatus().isOk
      .expectBody()
      .jsonPath("content[0].prisonerNumber").isEqualTo("A7089EY")
      .jsonPath("content[0].lastName").isEqualTo("SMITH")
      .jsonPath("content[0].firstName").doesNotExist()
  }

  private fun WebTestClient.prisonerDetailSearch(request: PrisonerDetailRequest, responseFields: List<String>) = post()
    .uri {
      it.path("/prisoner-detail")
        .queryParam("responseFields", responseFields)
        .build()
    }
    .body(BodyInserters.fromValue(gson.toJson(request)))
    .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
    .header("Content-Type", "application/json")
    .exchange()

  @Test
  fun `find by whole prisoner number`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089EY", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole lowercase prisoner number `() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "a7089Ey", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by prisoner number with a wildcard single letter`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089?Y", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by prisoner number with wildcard suffix`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(nomsNumber = "A7089*", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY", "A7089FA", "A7089FB"),
    )
  }

  @Test
  fun `find by whole PNC number with short year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773H", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole PNC number with long year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "2015/1234S", prisonIds = listOf("WSI")),
      expectedPrisoners = listOf("A9999AA"),
    )
  }

  @Test
  fun `find by lowercase PNC number with short year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773h", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by lowercase PNC with long year`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "2012/394773h", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by PNC number with wildcard single digit`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/39477?H", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by PNC number with a wildcard suffix and matching surname`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(pncNumber = "12/394773*", lastName = "smith", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by whole CRO number`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/12J", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by lowercase CRO number `() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/12j", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by CRO number with wildcard single letter`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/1?J", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by CRO number with wildcard suffix`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(croNumber = "29906/*J", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by criteria that do not match any prisoners - empty result`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "trevor", pncNumber = "29906/12J", prisonIds = listOf("MDI")),
      expectedPrisoners = emptyList(),
    )
  }

  @Test
  fun `find by first name`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "john", prisonIds = listOf("LEI", "MDI")),
      expectedPrisoners = listOf("A7089EY", "A7089EZ"),
    )
  }

  @Test
  fun `find by last name`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(lastName = "smith", prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A7089EY"),
    )
  }

  @Test
  fun `find by first name including aliases`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "Sam", prisonIds = listOf("LEI", "MDI")),
      expectedPrisoners = listOf("A7090AB", "A7090BA", "A7090BB", "A7090AF"),
    )
  }

  @Test
  fun `find by first name excluding aliases`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "Sam", prisonIds = listOf("LEI", "MDI"), includeAliases = false),
      expectedPrisoners = listOf("A7090AB", "A7090BA", "A7090BB"),
    )
  }

  @Test
  fun `find by last name including aliases`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(lastName = "Jones", prisonIds = listOf("MDI", "LEI")),
      expectedPrisoners = listOf("A1090AA", "A7090AA", "A7090AB", "A7090BA", "A7090BB", "A7090AF"),
    )
  }

  @Test
  fun `find by last name excluding aliases`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(lastName = "Jones", prisonIds = listOf("MDI", "LEI"), includeAliases = false),
      expectedPrisoners = listOf("A1090AA", "A7090AA", "A7090AB", "A7090BA", "A7090BB"),
    )
  }

  @Test
  fun `find by first and last names`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "sam", lastName = "jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `find by first and last names excluding aliases`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "sam", lastName = "jones", prisonIds = listOf("MDI", "AGI", "LEI"), includeAliases = false),
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC"),
    )
  }

  @Test
  fun `find by mixed case first and last names`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "Sam", lastName = "Jones", prisonIds = listOf("MDI", "AGI", "LEI")),
      expectedPrisoners = listOf("A7090AB", "A7090AC", "A7090AD", "A7090BA", "A7090BB", "A7090BC", "A7090AF"),
    )
  }

  @Test
  fun `find by first and last names in alias`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "danny", lastName = "colin", prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by mixed case first and last names in alias`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "DANny", lastName = "COLin", prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by first and last names in alias with wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "dann?", lastName = "col?n", prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by main first and last name with single wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "jimb?b", lastName = "j?cks", prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `find by mixed case first and last name with single wildcard letters`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(firstName = "JIMb?b", lastName = "j?cKs", prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("A7090AF"),
    )
  }

  @Test
  fun `no-terms query should match all prisoners in the specified location`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("A1090AA", "A7089EY", "A7089FA", "A7089FB", "A7090AA", "A7090AB", "A7090BB"),
    )
  }

  @Test
  fun `find by date of birth`() {
    detailSearch(
      detailRequest = PrisonerDetailRequest(prisonIds = listOf("MDI"), dateOfBirth = LocalDate.of(1980, 2, 28)),
      expectedPrisoners = listOf("A1090AA", "A7090AA", "A7090AB"),
    )
  }

  private fun detailSearch(
    detailRequest: PrisonerDetailRequest,
    expectedPrisoners: List<String> = emptyList(),
  ) {
    val response = webTestClient.post().uri("/prisoner-detail")
      .body(BodyInserters.fromValue(gson.toJson(detailRequest)))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.content).extracting("prisonerNumber").containsExactlyElementsOf(expectedPrisoners)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.numberOfElements).isEqualTo(expectedPrisoners.size)
  }
}
