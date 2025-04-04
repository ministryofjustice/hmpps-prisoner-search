package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IdentifierBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IncentiveLevelBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PrisonersInPrisonRequest
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

class PartialPrisonerResponseTest : AbstractSearchIntegrationTest() {
  override fun loadPrisonerData() {
    val prisonerData = listOf(
      PrisonerBuilder(
        prisonerNumber = "A1234AA",
        firstName = "SMITH",
        lastName = "JONES",
        agencyId = "MDI",
        dateOfBirth = "1975-07-20",
        recall = true,
        heightCentimetres = 180,
        currentIncentive = IncentiveLevelBuilder(
          levelCode = "STD",
          dateTime = LocalDateTime.parse("2022-01-01T12:00:00"),
        ),
        identifiers = listOf(
          IdentifierBuilder(type = "CRO", value = "345678/12T", createdDatetime = "2022-01-01T12:00:00"),
          IdentifierBuilder(type = "PNC", value = "25/123456R", createdDatetime = "2022-02-02T14:20:00"),
        ),
      ),
    )
    loadPrisonersFromBuilders(prisonerData)
  }

  @DisplayName("Prisoners in prison search")
  @Nested
  inner class PrisonerInPrisonSearch {
    @Test
    fun `should return bad request for unknown attributes`() {
      webTestClient.searchError(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf("prisonerNumber", "doesNotExist", "nested.alsoDoesNotExist"),
      ).also {
        assertThat(it.userMessage).doesNotContain("prisonerNumber")
        assertThat(it.userMessage).contains("doesNotExist")
        assertThat(it.userMessage).contains("nested.alsoDoesNotExist")
      }
    }

    @Test
    fun `should return attributes of different types`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "dateOfBirth",
          "recall",
          "heightCentimetres",
          "currentIncentive.level.description",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(dateOfBirth).isEqualTo("1975-07-20")
        assertThat(recall).isEqualTo(true)
        assertThat(heightCentimetres).isEqualTo(180)
        assertThat(currentIncentive?.level?.description).isEqualTo("Standard")
      }
    }

    @Test
    fun `should return all attributes if empty list of attributes requested`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(dateOfBirth).isEqualTo("1975-07-20")
        assertThat(recall).isEqualTo(true)
        assertThat(heightCentimetres).isEqualTo(180)
        assertThat(currentIncentive?.level?.description).isEqualTo("Standard")
      }
    }

    @Test
    fun `should return null for attributes not requested`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "currentIncentive.level.description",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(dateOfBirth).isNull()
        assertThat(recall).isNull()
        assertThat(heightCentimetres).isNull()
        assertThat(currentIncentive?.level?.description).isEqualTo("Standard")
        assertThat(currentIncentive?.level?.code).isNull()
      }
    }

    @Test
    fun `should return whole objects`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "currentIncentive",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(currentIncentive?.level?.description).isEqualTo("Standard")
        assertThat(currentIncentive?.level?.code).isEqualTo("STD")
        assertThat(currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2022-01-01T12:00:00"))
      }
    }

    @Test
    fun `should return nested objects`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "currentIncentive.level",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(currentIncentive?.level?.description).isEqualTo("Standard")
        assertThat(currentIncentive?.level?.code).isEqualTo("STD")
        assertThat(currentIncentive?.dateTime).isNull()
      }
    }

    @Test
    fun `should return lists of whole objects`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "identifiers",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(identifiers).extracting("type", "value", "createdDateTime").containsExactlyInAnyOrder(
          tuple("CRO", "345678/12T", LocalDateTime.parse("2022-01-01T12:00:00")),
          tuple("PNC", "25/123456R", LocalDateTime.parse("2022-02-02T14:20:00")),
        )
      }
    }

    @Test
    fun `should return nested properties from lists`() {
      val response = webTestClient.search(
        request = PrisonersInPrisonRequest(term = "A1234AA"),
        prisonId = "MDI",
        requestedAttributes = listOf(
          "prisonerNumber",
          "identifiers.type",
          "identifiers.value",
        ),
      )

      with(response.content.first()!!) {
        assertThat(prisonerNumber).isEqualTo("A1234AA")
        assertThat(identifiers).extracting("type", "value", "createdDateTime").containsExactlyInAnyOrder(
          tuple("CRO", "345678/12T", null),
          tuple("PNC", "25/123456R", null),
        )
      }
    }

    private fun WebTestClient.search(
      request: PrisonersInPrisonRequest = PrisonersInPrisonRequest(),
      sort: String? = null,
      prisonId: String = "MDI",
      requestedAttributes: List<String>? = null,
    ): RestResponsePage<Prisoner> {
      val responseType = object : ParameterizedTypeReference<RestResponsePage<Prisoner>>() {}

      return webTestClient.get().uri {
        it.path("/prison/$prisonId/prisoners")
          .queryParam("term", request.term)
          .queryParam("page", request.pagination.page)
          .queryParam("size", request.pagination.size)
          .queryParam("sort", sort)
          .apply { requestedAttributes?.forEach { queryParam("requestedAttributes", it) } }
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_IN_PRISON_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange().expectStatus().isOk
        .expectBody(responseType)
        .returnResult().responseBody!!
    }

    private fun WebTestClient.searchError(
      request: PrisonersInPrisonRequest = PrisonersInPrisonRequest(),
      sort: String? = null,
      prisonId: String = "MDI",
      requestedAttributes: List<String>? = null,
      status: HttpStatus = HttpStatus.BAD_REQUEST,
    ): ErrorResponse {
      val responseType = object : ParameterizedTypeReference<ErrorResponse>() {}

      return webTestClient.get().uri {
        it.path("/prison/$prisonId/prisoners")
          .queryParam("term", request.term)
          .queryParam("page", request.pagination.page)
          .queryParam("size", request.pagination.size)
          .queryParam("sort", sort)
          .apply { requestedAttributes?.forEach { queryParam("requestedAttributes", it) } }
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_IN_PRISON_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange().expectStatus().isEqualTo(status)
        .expectBody(responseType)
        .returnResult().responseBody!!
    }
  }
}
