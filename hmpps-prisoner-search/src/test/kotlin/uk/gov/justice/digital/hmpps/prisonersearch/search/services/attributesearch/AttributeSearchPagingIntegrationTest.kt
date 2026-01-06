package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IncentiveLevelBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.CONTAINS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.RequestDsl
import java.time.LocalDate
import java.time.LocalDateTime

class AttributeSearchPagingIntegrationTest : AbstractSearchIntegrationTest() {
  // Create 7 prisoners with predictable data we can sort by
  private final val prisonerCount = 7
  val prisoners = (
    (1..prisonerCount).map {
      PrisonerBuilder(
        prisonerNumber = "A$it",
        firstName = "John${it % 2}",
        dateOfBirth = LocalDate.parse("1989-12-31").plusDays(it.toLong()).toString(),
        heightCentimetres = 180 + it,
        recall = it % 2 == 0,
        currentIncentive = IncentiveLevelBuilder(levelDescription = "I$it", dateTime = LocalDateTime.now().minusDays((7 - it).toLong())),
      )
      // Add a prisoner that should not be returned in the results
    } + PrisonerBuilder(firstName = "fails_search")
    // Load the prisoners in a random order
    ).shuffled()

  // Default page size to 3 so each page has a start, middle and end.
  // Given 7 prisoners returned we expect 3 pages of: 3 prisoners (first page), 3 prisoners (not first or last page) and 1 prisoner (last page).
  private final val defaultPageSize = 3

  // Always search for the prisoners with predictable data only
  val request = RequestDsl {
    query {
      stringMatcher("firstName" CONTAINS "John")
    }
  }

  override fun loadPrisonerData() {
    loadPrisonersFromBuilders(prisoners)
  }

  @Test
  fun `should return bad request if sort attribute not recognised`() {
    webTestClient.attributeSearchFails(sortBy = listOf("unknown"))
      .expectBody()
      .jsonPath("status").isEqualTo(400)
      .jsonPath("developerMessage").isEqualTo("Sort attribute 'unknown' not found")
  }

  @Test
  fun `should return bad request if sort direction is not asc or desc`() {
    // Spring thinks anything not 'asc' or 'desc' is a field name to sort on
    webTestClient.attributeSearchFails(sortBy = listOf("firstName,ascending"))
      .expectBody()
      .jsonPath("status").isEqualTo(400)
      .jsonPath("developerMessage").isEqualTo("Sort attribute 'ascending' not found")
  }

  @Test
  fun `should sort by default ordering (prisoner number)`() {
    webTestClient.attributeSearchOk(page = 0)
      .expectResults("prisonerNumber", "A1", "A2", "A3")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1)
      .expectResults("prisonerNumber", "A4", "A5", "A6")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2)
      .expectResults("prisonerNumber", "A7")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by prisoner number descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("prisonerNumber,desc"))
      .expectResults("prisonerNumber", "A7", "A6", "A5")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("prisonerNumber,desc"))
      .expectResults("prisonerNumber", "A4", "A3", "A2")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("prisonerNumber,desc"))
      .expectResults("prisonerNumber", "A1")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a string`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName"))
      .expectResults("firstName", "John0", "John0", "John0")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName"))
      .expectResults("firstName", "John1")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a string descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName,desc"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName,desc"))
      .expectResults("firstName", "John1", "John0", "John0")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName,desc"))
      .expectResults("firstName", "John0")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a nested string`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("currentIncentive.level.description"))
      .expectResults("currentIncentive.level.description", "I1", "I2", "I3")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("currentIncentive.level.description"))
      .expectResults("currentIncentive.level.description", "I4", "I5", "I6")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("currentIncentive.level.description"))
      .expectResults("currentIncentive.level.description", "I7")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a nested string descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("currentIncentive.level.description,desc"))
      .expectResults("currentIncentive.level.description", "I7", "I6", "I5")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("currentIncentive.level.description,desc"))
      .expectResults("currentIncentive.level.description", "I4", "I3", "I2")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("currentIncentive.level.description,desc"))
      .expectResults("currentIncentive.level.description", "I1")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a date`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("dateOfBirth"))
      .expectResults("dateOfBirth", "1990-01-01", "1990-01-02", "1990-01-03")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("dateOfBirth"))
      .expectResults("dateOfBirth", "1990-01-04", "1990-01-05", "1990-01-06")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("dateOfBirth"))
      .expectResults("dateOfBirth", "1990-01-07")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a date descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("dateOfBirth,desc"))
      .expectResults("dateOfBirth", "1990-01-07", "1990-01-06", "1990-01-05")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("dateOfBirth,desc"))
      .expectResults("dateOfBirth", "1990-01-04", "1990-01-03", "1990-01-02")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("dateOfBirth,desc"))
      .expectResults("dateOfBirth", "1990-01-01")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a datetime`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("currentIncentive.dateTime"))
      .expectResults("currentIncentive.level.description", "I1", "I2", "I3")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("currentIncentive.dateTime"))
      .expectResults("currentIncentive.level.description", "I4", "I5", "I6")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("currentIncentive.dateTime"))
      .expectResults("currentIncentive.level.description", "I7")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a datetime descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("currentIncentive.dateTime,desc"))
      .expectResults("currentIncentive.level.description", "I7", "I6", "I5")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("currentIncentive.dateTime,desc"))
      .expectResults("currentIncentive.level.description", "I4", "I3", "I2")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("currentIncentive.dateTime,desc"))
      .expectResults("currentIncentive.level.description", "I1")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by an integer`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("heightCentimetres"))
      .expectResults("heightCentimetres", 181, 182, 183)
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("heightCentimetres"))
      .expectResults("heightCentimetres", 184, 185, 186)
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("heightCentimetres"))
      .expectResults("heightCentimetres", 187)
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by an integer descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("heightCentimetres,desc"))
      .expectResults("heightCentimetres", 187, 186, 185)
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("heightCentimetres,desc"))
      .expectResults("heightCentimetres", 184, 183, 182)
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("heightCentimetres,desc"))
      .expectResults("heightCentimetres", 181)
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a boolean`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("recall"))
      .expectResults("recall", false, false, false)
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("recall"))
      .expectResults("recall", false, true, true)
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("recall"))
      .expectResults("recall", true)
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a boolean descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("recall,desc"))
      .expectResults("recall", true, true, true)
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("recall,desc"))
      .expectResults("recall", false, false, false)
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("recall,desc"))
      .expectResults("recall", false)
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a string and a date`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName", "dateOfBirth"))
      .expectResults("firstName", "John0", "John0", "John0")
      .expectResults("dateOfBirth", "1990-01-02", "1990-01-04", "1990-01-06")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName", "dateOfBirth"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectResults("dateOfBirth", "1990-01-01", "1990-01-03", "1990-01-05")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName", "dateOfBirth"))
      .expectResults("firstName", "John1")
      .expectResults("dateOfBirth", "1990-01-07")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a string and a date in a single sort parameter`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName,dateOfBirth"))
      .expectResults("firstName", "John0", "John0", "John0")
      .expectResults("dateOfBirth", "1990-01-02", "1990-01-04", "1990-01-06")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName", "dateOfBirth"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectResults("dateOfBirth", "1990-01-01", "1990-01-03", "1990-01-05")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName", "dateOfBirth"))
      .expectResults("firstName", "John1")
      .expectResults("dateOfBirth", "1990-01-07")
      .expectPage(page = 2)
  }

  @Test
  fun `should sort by a string and a date both descending`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName,desc", "dateOfBirth,desc"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectResults("dateOfBirth", "1990-01-07", "1990-01-05", "1990-01-03")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName,desc", "dateOfBirth,desc"))
      .expectResults("firstName", "John1", "John0", "John0")
      .expectResults("dateOfBirth", "1990-01-01", "1990-01-06", "1990-01-04")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName,desc", "dateOfBirth,desc"))
      .expectResults("firstName", "John0")
      .expectResults("dateOfBirth", "1990-01-02")
      .expectPage(page = 2)
  }

  @Test
  fun `should not allow multiple ordered sort fields in a single sort parameter`() {
    webTestClient.attributeSearchFails(page = 0, sortBy = listOf("firstName,desc,dateOfBirth,desc"))
      .expectBody()
      .jsonPath("status").isEqualTo(400)
      .jsonPath("developerMessage").isEqualTo("Sort attribute 'desc' not found")
  }

  @Test
  fun `should sort by a string and a date in different orders`() {
    webTestClient.attributeSearchOk(page = 0, sortBy = listOf("firstName,desc", "dateOfBirth,asc"))
      .expectResults("firstName", "John1", "John1", "John1")
      .expectResults("dateOfBirth", "1990-01-01", "1990-01-03", "1990-01-05")
      .expectPage(page = 0)

    webTestClient.attributeSearchOk(page = 1, sortBy = listOf("firstName,desc", "dateOfBirth,asc"))
      .expectResults("firstName", "John1", "John0", "John0")
      .expectResults("dateOfBirth", "1990-01-07", "1990-01-02", "1990-01-04")
      .expectPage(page = 1)

    webTestClient.attributeSearchOk(page = 2, sortBy = listOf("firstName,desc", "dateOfBirth,asc"))
      .expectResults("firstName", "John0")
      .expectResults("dateOfBirth", "1990-01-06")
      .expectPage(page = 2)
  }

  private fun <T> WebTestClient.ResponseSpec.expectResults(field: String, vararg fieldValues: T) = expectBody().expectResults(field, *fieldValues)

  private fun <T> WebTestClient.BodyContentSpec.expectResults(field: String, vararg fieldValues: T) = jsonPath("$.content[*].$field").value<List<T>> {
    assertThat(it).containsExactlyInAnyOrderElementsOf(listOf(*fieldValues))
  }
    .jsonPath("numberOfElements").isEqualTo(fieldValues.size)
    .jsonPath("totalElements").isEqualTo(prisonerCount)

  private fun WebTestClient.BodyContentSpec.expectPage(page: Int, size: Int = defaultPageSize) = jsonPath("pageable.pageNumber").isEqualTo(page)
    .also {
      when (page) {
        0 -> {
          it.jsonPath("first").isEqualTo(true)
          it.jsonPath("last").isEqualTo(false)
        }
        prisoners.size / size -> {
          it.jsonPath("first").isEqualTo(false)
          it.jsonPath("last").isEqualTo(true)
        }
        else -> {
          it.jsonPath("first").isEqualTo(false)
          it.jsonPath("last").isEqualTo(false)
        }
      }
    }

  private fun WebTestClient.attributeSearchFails(page: Int = 0, size: Int = defaultPageSize, sortBy: List<String> = listOf()) = attributeSearch(page, size, sortBy)
    .expectStatus().isBadRequest

  private fun WebTestClient.attributeSearchOk(page: Int = 0, size: Int = defaultPageSize, sortBy: List<String> = listOf()) = attributeSearch(page, size, sortBy)
    .expectStatus().isOk

  private fun WebTestClient.attributeSearch(
    page: Int,
    size: Int,
    sortBy: List<String>,
  ): WebTestClient.ResponseSpec = post()
    .uri {
      it.path("/attribute-search")
        .queryParam("page", page)
        .queryParam("size", size)
        .apply { sortBy.forEach { sortAttributes -> queryParam("sort", sortAttributes) } }
        .build()
    }
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
    .header("Content-Type", "application/json")
    .bodyValue(objectMapper.writeValueAsString(request))
    .exchange()
}
