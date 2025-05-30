package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.withinPercentage
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.RestrictedPatientSearchCriteria

class RestrictedPatientsSearchResourceTest : AbstractSearchDataIntegrationTest() {
  @Nested
  inner class Authorisation {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation())
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can perform a match for ROLE_GLOBAL_SEARCH role`() {
      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `can perform a match for ROLE_PRISONER_SEARCH role`() {
      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `can perform a match for ROLE_GLOBAL_SEARCH and ROLE_PRISONER_SEARCH role`() {
      webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
        .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  inner class SearchAll {
    @Test
    fun `finds all restricted patients when no criteria provided`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, null),
        "/results/restrictedPatientsSearch/search_results_all.json",
      )
    }
  }

  @Nested
  inner class PrisonerNumber {
    @Test
    fun `does not match when number is of active prisoner`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("A7089EY", null, null),
        "/results/restrictedPatientsSearch/empty.json",
      )
    }

    @Test
    fun `can perform a match on prisoner number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("A9999RB", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match on prisoner number lowercase`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("a9999rb", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match wrong prisoner number but correct name`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("X7089EY", "HOSP", "PATIENTONE"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `should return bad request for invalid response fields`() {
      webTestClient.matchRestrictedPatients(RestrictedPatientSearchCriteria("A9999RB", null, null), listOf("prisonerNumber", "doesNotExist"))
        .expectStatus().isBadRequest
        .expectBody().jsonPath("userMessage").value<String> {
          assertThat(it).contains("Invalid response fields requested: [doesNotExist]")
        }
    }

    @Test
    fun `should only return requested response fields - by empty search criteria`() {
      webTestClient.matchRestrictedPatients(RestrictedPatientSearchCriteria(null, null, null), listOf("prisonerNumber", "lastName"))
        .expectStatus().isOk
        .expectBody()
        .jsonPath("content.length()").isEqualTo(4)
        .jsonPath("content[0].prisonerNumber").isEqualTo("A7090BF")
        .jsonPath("content[0].lastName").isEqualTo("FELLOWS")
        .jsonPath("content[0].firstName").doesNotExist()
    }

    @Test
    fun `should only return requested response fields - by prisoner identifier`() {
      webTestClient.matchRestrictedPatients(RestrictedPatientSearchCriteria("A9999RB", null, null), listOf("prisonerNumber", "lastName"))
        .expectStatus().isOk
        .expectBody()
        .jsonPath("content[0].prisonerNumber").isEqualTo("A9999RB")
        .jsonPath("content[0].lastName").isEqualTo("PATIENTONE")
        .jsonPath("content[0].firstName").doesNotExist()
    }

    @Test
    fun `should only return requested response fields - by name`() {
      webTestClient.matchRestrictedPatients(RestrictedPatientSearchCriteria(null, "hosp", "patienttwo"), listOf("prisonerNumber", "lastName"))
        .expectStatus().isOk
        .expectBody()
        .jsonPath("content[0].prisonerNumber").isEqualTo("A9999RC")
        .jsonPath("content[0].lastName").isEqualTo("PATIENTTWO")
        .jsonPath("content[0].firstName").doesNotExist()
    }

    private fun WebTestClient.matchRestrictedPatients(criteria: RestrictedPatientSearchCriteria, responseFields: List<String>) = post()
      .uri {
        it.path("/restricted-patient-search/match-restricted-patients")
          .queryParam("responseFields", responseFields)
          .build()
      }
      .body(BodyInserters.fromValue(gson.toJson(criteria)))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
  }

  @Nested
  inner class PNCNumber {
    @Test
    fun `can perform a match on PNC number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("2014/009773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match on PNC number short year`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("14/9773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match on PNC number long year`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("2014/9773W", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }
  }

  @Nested
  inner class CRONumber {
    @Test
    fun `can perform a match on CRO number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("29913/12L", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }
  }

  @Nested
  inner class Booking {
    @Test
    fun `can perform a match on book number`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("V69687", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match on booking Id`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria("1999992", null, null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }
  }

  @Nested
  inner class Name {
    @Test
    fun `can not match when name is mis-spelt`() {
      restrictedPatientSearch(RestrictedPatientSearchCriteria(null, "PSYHOS", "PATIENTONE"), "/results/restrictedPatientsSearch/empty.json")
    }

    @Test
    fun `does not match when name is of active prisoner`() {
      restrictedPatientSearch(RestrictedPatientSearchCriteria(null, "JOHN", "SMYTH"), "/results/restrictedPatientsSearch/empty.json")
    }

    @Test
    fun `can perform a match on a first name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "hosp", null),
        "/results/restrictedPatientsSearch/search_results_hosp.json",
      )
    }

    @Test
    fun `can perform a match on a last name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, "patienttwo"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_two.json",
      )
    }

    @Test
    fun `can perform a match on first and last name only`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "hosp", "patienttwo"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_two.json",
      )
    }
  }

  @Nested
  inner class Alias {
    @Test
    fun `does not match aliases from active prisoners`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "master", null),
        "/results/restrictedPatientsSearch/empty.json",
      )
    }

    @Test
    fun `can perform a match on a first and last name only multiple hits include aliases`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "PSYHOSP", "PATIENTONE"),
        "/results/restrictedPatientsSearch/search_results_patient_one_aliases.json",
      )
    }

    @Test
    fun `can perform a match on first and last name in alias but they must be from the same record`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "PSYHOSP", "OTHERALIAS"),
        "/results/restrictedPatientsSearch/empty.json",
      )
    }

    @Test
    fun `can perform a match on firstname only in alias`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, "AN", null),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }

    @Test
    fun `can perform a match on last name only in alias`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, "OTHERALIAS"),
        "/results/restrictedPatientsSearch/search_results_hosp_patient_one.json",
      )
    }
  }

  @Nested
  inner class SupportingPrisonIds {
    @Test
    fun `finds restricted patients with a single supporting prison`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, null, listOf("DNI")),
        "/results/restrictedPatientsSearch/search_results_supporting_prison_DNI.json",
      )
    }

    @Test
    fun `finds restricted patients with a multiple supporting prisons`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, null, listOf("DNI", "MDI", "NONE")),
        "/results/restrictedPatientsSearch/search_results_supporting_prison_DNI_MDI.json",
      )
    }

    @Test
    fun `finds restricted patients filtered by name and supporting prison`() {
      restrictedPatientSearch(
        RestrictedPatientSearchCriteria(null, null, "patientone", listOf("DNI")),
        "/results/restrictedPatientsSearch/search_results_supporting_prison_DNI.json",
      )
    }
  }

  @Nested
  inner class Pagination {
    @Test
    fun `can perform search which returns 1 result from first page`() {
      restrictedPatientSearchPagination(
        RestrictedPatientSearchCriteria(null, "HOSP", null),
        1,
        0,
        "/results/restrictedPatientsSearch/search_results_hosp_pagination1.json",
      )
    }

    @Test
    fun `can perform search which returns 1 result from second page`() {
      restrictedPatientSearchPagination(
        RestrictedPatientSearchCriteria(null, "HOSP", null),
        1,
        1,
        "/results/restrictedPatientsSearch/search_results_hosp_pagination2.json",
      )
    }
  }

  @Test
  fun `telemetry is recorded`() {
    webTestClient.post().uri("/restricted-patient-search/match-restricted-patients")
      .body(BodyInserters.fromValue(gson.toJson(RestrictedPatientSearchCriteria(null, null, null))))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH", "ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk

    verify(telemetryClient).trackEvent(
      eq("POSFindRestrictedPatientsByCriteria"),
      any(),
      check<Map<String, Double>> {
        assertThat(it["numberOfResults"]).isCloseTo(4.0, withinPercentage(1))
      },
    )
  }
}
