package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.AlertsApiExtension.Companion.alertsApi
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
    alertsApi.stubGetAlertTypes(alertTypes)

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
    alertsApi.stubGetAlertTypes(alertTypes)

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
    alertsApi.stubGetAlertTypes(alertTypes)

    val result = webTestClient.getAlertTypes()

    assertThat(result.alertTypes).extracting("description").containsExactly("Child Communication Measures", "Social Care", "X")
    assertThat(result.alertTypes[0].codes).extracting("description").containsExactly("CSIP", "Child contact L1")
    assertThat(result.alertTypes[1].codes).extracting("description").containsExactly("Adult At Risk (Home Office identified)", "Adult Social Care")
    assertThat(result.alertTypes[2].codes).extracting("description").containsExactly("X1")
  }

  @Test
  fun `should cache alerts reference data`() {
    alertsApi.stubGetAlertTypes(alertTypes)
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
        "code": "C",
        "description": "Child Communication Measures",
        "listSequence": 3,
        "isActive": true,
        "createdAt": "2008-02-01T10:25:00",
        "createdBy": "HQJ90D",
        "modifiedAt": "2010-03-07T16:28:00",
        "modifiedBy": "OMS_OWNER",
        "deactivatedAt": null,
        "deactivatedBy": null,
        "alertCodes": [
          {
            "alertTypeCode": "C",
            "code": "CC1",
            "description": "Child contact L1",
            "listSequence": 3,
            "isActive": true,
            "createdAt": "2015-10-27T11:03:35",
            "createdBy": "MQE96U_ADM",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          },
          {
            "alertTypeCode": "O",
            "code": "CSIP",
            "description": "CSIP",
            "listSequence": 10,
            "isActive": true,
            "createdAt": "2019-01-11T10:18:56",
            "createdBy": "HQU53Y_CEN",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          }
        ]
      },
      {
        "code": "A",
        "description": "Social Care",
        "listSequence": 12,
        "isActive": true,
        "createdAt": "2015-08-01T18:27:52",
        "createdBy": "OMS_OWNER",
        "modifiedAt": null,
        "modifiedBy": null,
        "deactivatedAt": null,
        "deactivatedBy": null,
        "alertCodes": [
          {
            "alertTypeCode": "A",
            "code": "ADSC",
            "description": "Adult Social Care",
            "listSequence": 1,
            "isActive": false,
            "createdAt": "2020-02-07T11:03:45",
            "createdBy": "AQS39G_CEN",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          },
          {
            "alertTypeCode": "V",
            "code": "AAR",
            "description": "Adult At Risk (Home Office identified)",
            "listSequence": 6,
            "isActive": true,
            "createdAt": "2017-07-24T19:38:32",
            "createdBy": "HQU53Y_CEN",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          }
        ]
      }
    ]
  """.trimIndent()
}
