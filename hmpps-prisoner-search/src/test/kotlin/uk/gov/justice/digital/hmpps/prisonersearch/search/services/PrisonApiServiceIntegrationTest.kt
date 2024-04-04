package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension.Companion.prisonApi

class PrisonApiServiceIntegrationTest : IntegrationTestBase() {
  @Nested
  inner class GetAlerts {
    @BeforeEach
    fun setUp() {
      prisonApi.stubAlertTypes(alertTypes)
      prisonApi.stubAlertCodes(alertCodes)
    }

    @Test
    fun `should get alerts`() {
      val alerts = prisonApiService.getAllAlerts()

      assertThat(alerts).extracting(AlertType::type, AlertType::description).containsExactlyInAnyOrder(
        tuple("A", "Social Care"),
        tuple("C", "Child Communication Measures"),
      )
      with(alerts.first { it.type == "A" }) {
        assertThat(this.alertCodes).containsExactlyInAnyOrder(
          AlertCode("AAR", "Adult At Risk (Home Office identified)", true),
          AlertCode("ADSC", "Adult Social Care", false),
        )
      }
      with(alerts.first { it.type == "C" }) {
        assertThat(this.alertCodes).containsExactlyInAnyOrder(
          AlertCode("CSIP", "CSIP", true),
        )
      }
    }
  }

  val alertTypes =
    """
      [
        {
          "domain": "ALERT",
          "code": "A",
          "description": "Social Care",
          "activeFlag": "Y",
          "listSeq": 12,
          "systemDataFlag": "N",
          "subCodes": []
        },
        {
          "domain": "ALERT",
          "code": "C",
          "description": "Child Communication Measures",
          "activeFlag": "Y",
          "listSeq": 3,
          "systemDataFlag": "N",
          "subCodes": []
        }
      ]
    """.trimIndent()

  val alertCodes =
    """
      [
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
        },
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
    """.trimIndent()
}
