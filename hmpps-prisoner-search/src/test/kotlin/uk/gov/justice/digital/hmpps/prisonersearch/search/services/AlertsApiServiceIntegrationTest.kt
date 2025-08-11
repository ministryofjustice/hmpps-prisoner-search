package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.AlertsApiExtension.Companion.alertsApi

class AlertsApiServiceIntegrationTest : IntegrationTestBase() {
  @Nested
  inner class GetAlerts {
    @BeforeEach
    fun setUp() {
      alertsApi.stubGetAlertTypes(alertTypes)
    }

    @Test
    fun `should get alerts`() {
      val alerts = alertsApiService.getAllAlerts()

      assertThat(alerts).extracting(PrisonAlertType::type, PrisonAlertType::description).containsExactlyInAnyOrder(
        tuple("A", "Social Care"),
        tuple("C", "Child Communication Measures"),
      )
      with(alerts.first { it.type == "A" }) {
        assertThat(this.alertCodes).containsExactlyInAnyOrder(
          PrisonAlertCode("AAR", "Adult At Risk (Home Office identified)", true),
          PrisonAlertCode("ADSC", "Adult Social Care", false),
        )
      }
      with(alerts.first { it.type == "C" }) {
        assertThat(this.alertCodes).containsExactlyInAnyOrder(
          PrisonAlertCode("CSIP", "CSIP", true),
        )
      }

      alertsApi.verify(
        getRequestedFor(urlEqualTo("/alert-types?includeInactive=true")),
      )
    }
  }

  val alertTypes = """
    [
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
            "code": "AAR",
            "description": "Adult At Risk (Home Office identified)",
            "listSequence": 1,
            "isActive": true,
            "createdAt": "2020-02-07T11:03:45",
            "createdBy": "AQS39G_CEN",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          },
          {
            "alertTypeCode": "A",
            "code": "ADSC",
            "description": "Adult Social Care",
            "listSequence": 1,
            "isActive": false,
            "createdAt": "2015-08-01T18:27:52",
            "createdBy": "OMS_OWNER",
            "modifiedAt": null,
            "modifiedBy": null,
            "deactivatedAt": null,
            "deactivatedBy": null
          }
        ]
      },
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
            "code": "CSIP",
            "description": "CSIP",
            "listSequence": 3,
            "isActive": true,
            "createdAt": "2015-10-27T11:03:35",
            "createdBy": "MQE96U_ADM",
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
