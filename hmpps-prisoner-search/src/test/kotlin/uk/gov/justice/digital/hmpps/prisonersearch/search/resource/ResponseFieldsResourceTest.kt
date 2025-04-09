package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase

class ResponseFieldsResourceTest : IntegrationTestBase() {

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/response-fields")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/response-fields")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/response-fields")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class HappyPath {
    @Test
    fun `should return response fields of each simple type`() {
      webTestClient.getResponseFields { result: List<String> ->
        assertThat(result).contains(
          "prisonerNumber",
          "dateOfBirth",
          "youthOffender",
          "currentIncentive.dateTime",
          "shoeSize",
        )
      }
    }

    @Test
    fun `should return nested objects`() {
      webTestClient.getResponseFields { result: List<String> ->
        assertThat(result).contains(
          "currentIncentive",
          "currentIncentive.level",
          "currentIncentive.level.description",
        )
      }
    }

    @Test
    fun `should return lists of objects`() {
      webTestClient.getResponseFields { result: List<String> ->
        assertThat(result).contains(
          "tattoos",
          "tattoos.bodyPart",
        )
      }
    }

    @Test
    fun `should return nested lists of objects`() {
      webTestClient.getResponseFields { result: List<String> ->
        assertThat(result).contains(
          "addresses",
          "addresses.phoneNumbers",
          "addresses.phoneNumbers.number",
        )
      }
    }

    @Test
    fun `should not return the derived type active`() {
      webTestClient.getResponseFields { result: List<String> ->
        assertThat(result).doesNotContain("active")
      }
    }
  }

  private fun WebTestClient.getResponseFields(tests: (List<String>) -> Unit) = webTestClient.get().uri("/response-fields")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
    .header("Content-Type", "application/json")
    .exchange()
    .expectStatus().isOk
    .expectBody(object : ParameterizedTypeReference<List<String>>() {})
    .consumeWith {
      tests(it.responseBody!!)
    }
}
