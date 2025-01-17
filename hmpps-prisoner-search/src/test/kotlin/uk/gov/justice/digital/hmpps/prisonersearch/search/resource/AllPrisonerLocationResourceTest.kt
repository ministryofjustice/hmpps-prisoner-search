package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest

class AllPrisonerLocationResourceTest : AbstractSearchDataIntegrationTest() {

  @DisplayName("GET /prisoner-location/all")
  @Nested
  inner class GetAllPrisonerLocations {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/prisoner-location/all")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prisoner-location/all")
          .headers(setAuthorisation())
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden when wrong role`() {
        webTestClient.get().uri("/prisoner-location/all")
          .headers(setAuthorisation("ROLE_PRISONER_SEARCH"))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can perform a search for PRISONER_SEARCH__PRISONER_LOCATION__RO role`() = runTest {
        val response = webTestClient.get().uri("/prisoner-location/all")
          .headers(setAuthorisation(roles = listOf("PRISONER_SEARCH__PRISONER_LOCATION__RO")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .returnResult(PrisonerLocationResponse::class.java).responseBody.blockFirst()!!

        assertThat(response.scrollId).isNotBlank()
        assertThat(response.locations).hasSize(10)

        var scrollId = response.scrollId
        var hits = response.locations ?: emptyList()

        while (scrollId != null) {
          val nextResponse = webTestClient.get().uri("/prisoner-location/scroll/$scrollId")
            .headers(setAuthorisation(roles = listOf("PRISONER_SEARCH__PRISONER_LOCATION__RO")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .returnResult(PrisonerLocationResponse::class.java).responseBody.blockFirst()!!

          scrollId = nextResponse.scrollId
          hits += nextResponse.locations ?: emptyList()
        }

        assertThat(hits).contains(
          PrisonerLocation("A7089EZ", "LEI", null),
          PrisonerLocation("A9999RB", "OUT", "DNI"),
        ).hasSizeGreaterThan(20)

        // check that we have actually scrolled
        verify(openSearchClient, atLeastOnce()).scroll(any(), any())

        // and check that we have cleared the scroll at the end
        verify(openSearchClient).clearScroll(any(), any())
      }
    }
  }
}
