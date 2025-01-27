package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.AlertCode
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.AlertType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAlertsResponse

class AlertsReferenceDataResourceTest : AbstractSearchIntegrationTest() {

  override fun loadPrisonerData() {
    val prisonerData = listOf(
      PrisonerBuilder(alertCodes = listOf("A" to "ADSC", "C" to "CSIP")),
      PrisonerBuilder(alertCodes = listOf("A" to "AAR", "A" to "ADSC")),
      PrisonerBuilder(alertCodes = listOf("C" to "CC1", "C" to "CSIP", "X" to "X1")),
    )
    loadPrisonersFromBuilders(prisonerData)
  }

  @Test
  fun `access forbidden when no authority`() {
    webTestClient.get().uri("/reference-data/alerts/types")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.get().uri("/reference-data/alerts/types")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `access forbidden with wrong role`() {
    webTestClient.get().uri("/reference-data/alerts/types")
      .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `should return alert types and codes`() {
    prisonApi.stubGetAlertTypes(alertTypes)

    val result = webTestClient.getAlertTypes()

    assertThat(result.alertTypes).extracting("type", "description", "active").contains(
      tuple("A", "Social Care", true),
      tuple("C", "Child Communication Measures", true),
    )
    assertThat(result.alertTypes.first { it.type == "A" }.codes).containsExactlyInAnyOrder(
      AlertCode("A", "AAR", "Adult At Risk (Home Office identified)", true),
      AlertCode("A", "ADSC", "Adult Social Care", false),
    )
    assertThat(result.alertTypes.first { it.type == "C" }.codes).containsExactlyInAnyOrder(
      AlertCode("C", "CC1", "Child contact L1", true),
      AlertCode("C", "CSIP", "CSIP", true),
    )
  }

  @Test
  fun `should return alert types and codes even if not found in prison api`() {
    prisonApi.stubGetAlertTypes(alertTypes)

    val result = webTestClient.getAlertTypes()

    assertThat(result.alertTypes).contains(
      AlertType(
        type = "X",
        description = "X",
        active = false,
        codes = listOf(
          AlertCode("X", "X1", "X1", false),
        ),
      ),
    )
  }

  @Test
  fun `should return alert types and codes ordered by descriptions`() {
    prisonApi.stubGetAlertTypes(alertTypes)

    val result = webTestClient.getAlertTypes()

    assertThat(result.alertTypes).extracting("description").containsExactly("Child Communication Measures", "Social Care", "X")
    assertThat(result.alertTypes[0].codes).extracting("description").containsExactly("CSIP", "Child contact L1")
    assertThat(result.alertTypes[1].codes).extracting("description").containsExactly("Adult At Risk (Home Office identified)", "Adult Social Care")
    assertThat(result.alertTypes[2].codes).extracting("description").containsExactly("X1")
  }

  @Test
  fun `should cache alerts reference data`() {
    prisonApi.stubGetAlertTypes(alertTypes)
    cacheManager.getCache("alertsReferenceData")?.clear()

    // pre-check to ensure that forcing elastic error causes search to fail
    forceElasticError()
    webTestClient.get().uri("/reference-data/alerts/types")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().is5xxServerError

    // so that we can check caching then works
    reset(elasticsearchClient)
    webTestClient.getAlertTypes()

    // and no exception is thrown
    forceElasticError()
    webTestClient.getAlertTypes()
  }

  private fun WebTestClient.getAlertTypes() = get()
    .uri("/reference-data/alerts/types")
    .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
    .exchange()
    .expectStatus().isOk
    .returnResult<ReferenceDataAlertsResponse>()
    .responseBody
    .blockFirst()!!

  val alertTypes = """
    [
      {
        "domain": "ALERT",
        "code": "C",
        "description": "Child Communication Measures",
        "activeFlag": "Y",
        "listSeq": 3,
        "systemDataFlag": "N",
        "subCodes": [
          {
            "domain": "ALERT_CODE",
            "code": "CC1",
            "description": "Child contact L1",
            "parentDomain": "ALERT",
            "parentCode": "C",
            "activeFlag": "Y",
            "listSeq": 3,
            "systemDataFlag": "N",
            "subCodes": []
          },
          {
            "domain": "ALERT_CODE",
            "code": "CSIP",
            "description": "CSIP",
            "parentDomain": "ALERT",
            "parentCode": "C",
            "activeFlag": "Y",
            "listSeq": 10,
            "systemDataFlag": "N",
            "subCodes": []
          }
        ]
      },
      {
        "domain": "ALERT",
        "code": "A",
        "description": "Social Care",
        "activeFlag": "Y",
        "listSeq": 12,
        "systemDataFlag": "N",
        "subCodes": [
          {
            "domain": "ALERT_CODE",
            "code": "ADSC",
            "description": "Adult Social Care",
            "parentDomain": "ALERT",
            "parentCode": "A",
            "activeFlag": "N",
            "listSeq": 1,
            "systemDataFlag": "N",
            "subCodes": []
          },
          {
            "domain": "ALERT_CODE",
            "code": "AAR",
            "description": "Adult At Risk (Home Office identified)",
            "parentDomain": "ALERT",
            "parentCode": "A",
            "activeFlag": "Y",
            "listSeq": 6,
            "systemDataFlag": "N",
            "subCodes": []
          }
        ]
      }
    ]
  """.trimIndent()
}
