package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.readResourceAsText
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReleaseDateSearch
import java.time.LocalDate

class PrisonerSearchByReleaseDateResourceTest : AbstractSearchDataIntegrationTest() {
  @Test
  fun `access forbidden when no authority`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .bodyValue(ReleaseDateSearch(latestReleaseDate = LocalDate.now()))
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no upper bound provided`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .bodyValue(ReleaseDateSearch(latestReleaseDate = null))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search - latestReleaseDateRange is a required field")
  }

  @Test
  fun `bad request when the upper bound is a date before the lower bound`() {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison")
      .bodyValue(
        ReleaseDateSearch(
          earliestReleaseDate = LocalDate.parse("2022-01-02"),
          latestReleaseDate = LocalDate.parse("2022-01-01"),
        ),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("developerMessage")
      .isEqualTo("Invalid search - latestReleaseDate must be on or before the earliestReleaseDate")
  }

  @Test
  fun `can match on conditionalReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2023-05-16"),
        latestReleaseDate = LocalDate.parse("2023-05-16"),
      ),
      "/results/releaseDateSearch/search_conditional_release_date.json",
    )
  }

  @Test
  fun `can match on confirmedReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2023-04-01"),
        latestReleaseDate = LocalDate.parse("2023-04-01"),
      ),
      "/results/releaseDateSearch/search_confirmed_release_date.json",
    )
  }

  @Test
  fun `can match on date range with mix of confirmedReleaseDate and conditionalReleaseDate`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2020-04-28"),
        latestReleaseDate = LocalDate.parse("2023-04-01"),
      ),
      "/results/releaseDateSearch/search_date_range.json",
    )
  }

  @Test
  fun `can filter date range by prison code`() {
    searchByReleaseDate(
      ReleaseDateSearch(
        earliestReleaseDate = LocalDate.parse("2011-01-05"),
        latestReleaseDate = LocalDate.parse("2030-01-31"),
        prisonIds = setOf("MDI", "WSI"),
      ),
      "/results/releaseDateSearch/search_date_range_filtered_by_prison.json",
    )
  }

  @Nested
  inner class Pagination {
    @Test
    fun `can paginate results - 8 results on page 1`() {
      searchByReleaseDatePagination(
        ReleaseDateSearch(
          earliestReleaseDate = LocalDate.parse("2011-01-05"),
          latestReleaseDate = LocalDate.parse("2030-01-31"),
        ),
        8,
        0,
        "/results/releaseDateSearch/search_date_range_pagination_page_1.json",
      )
    }

    @Test
    fun `can paginate results - 3 results on page 2`() {
      searchByReleaseDatePagination(
        ReleaseDateSearch(
          earliestReleaseDate = LocalDate.parse("2011-01-05"),
          latestReleaseDate = LocalDate.parse("2030-01-31"),
        ),
        8,
        1,
        "/results/releaseDateSearch/search_date_range_pagination_page_2.json",
      )
    }

    @Test
    fun `will default the page size`() {
      searchByReleaseDate()
        .expectBody().jsonPath("pageable.pageSize").isEqualTo("10")
    }

    @Test
    fun `will default the page size if empty parameter provided`() {
      searchByReleaseDate(queryParams = "?size=")
        .expectBody().jsonPath("pageable.pageSize").isEqualTo("10")
    }

    @Test
    fun `will default the page size if invalid size provided`() {
      searchByReleaseDate(queryParams = "?size=0&page=1")
        .expectBody().jsonPath("pageable.pageSize").isEqualTo("10")
    }

    @Test
    fun `will default the page number`() {
      searchByReleaseDate()
        .expectBody().jsonPath("pageable.pageNumber").isEqualTo("0")
    }

    @Test
    fun `will default the page number if empty parameter provided`() {
      searchByReleaseDate(queryParams = "?page=")
        .expectBody().jsonPath("pageable.pageNumber").isEqualTo("0")
    }

    @Test
    fun `will default the page number if invalid value provided`() {
      searchByReleaseDate(queryParams = "?size=1&page=-1")
        .expectBody().jsonPath("pageable.pageNumber").isEqualTo("0")
    }
  }

  @Test
  fun `should return bad request for invalid response fields`() {
    webTestClient.post()
      .uri {
        it.path("/prisoner-search/release-date-by-prison")
          .queryParam("responseFields", "prisonerNumber", "doesNotExist")
          .build()
      }
      .bodyValue(ReleaseDateSearch(latestReleaseDate = LocalDate.now()))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("developerMessage").isEqualTo("Invalid response fields requested: [doesNotExist]")
  }

  @Test
  fun `should only return requested response fields`() {
    val search = ReleaseDateSearch(earliestReleaseDate = LocalDate.parse("2023-05-16"), latestReleaseDate = LocalDate.parse("2023-05-16"))
    webTestClient.post()
      .uri {
        it.path("/prisoner-search/release-date-by-prison")
          .queryParam("responseFields", "prisonerNumber", "lastName")
          .build()
      }
      .bodyValue(search)
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("content.length()").isEqualTo(1)
      .jsonPath("content[0].prisonerNumber").isEqualTo("A7089EY")
      .jsonPath("content[0].lastName").isEqualTo("SMITH")
      .jsonPath("content[0].firstName").doesNotExist()
  }

  fun searchByReleaseDate(
    searchCriteria: ReleaseDateSearch = ReleaseDateSearch(
      earliestReleaseDate = LocalDate.parse("2011-01-05"),
      latestReleaseDate = LocalDate.parse("2030-01-31"),
    ),
    fileAssert: String? = null,
    queryParams: String = "",
  ) = webTestClient.post().uri("/prisoner-search/release-date-by-prison$queryParams")
    .bodyValue(searchCriteria)
    .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
    .header("Content-Type", "application/json")
    .exchange()
    .expectStatus().isOk
    .also { if (fileAssert != null) it.expectBody().json(fileAssert.readResourceAsText()) }

  fun searchByReleaseDatePagination(searchCriteria: ReleaseDateSearch, size: Long, page: Long, fileAssert: String) {
    webTestClient.post().uri("/prisoner-search/release-date-by-prison?size=$size&page=$page")
      .bodyValue(searchCriteria)
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody().json(fileAssert.readResourceAsText())
  }
}
