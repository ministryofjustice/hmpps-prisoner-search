package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAlertCode
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAlertType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataService

class AlertsReferenceDataResourceTest : AbstractSearchIntegrationTest() {

  @Autowired
  private lateinit var referenceDataService: ReferenceDataService

  // TODO SDIT-1583 This test class will eventually be expanded to use the real endpoint.
  override fun loadPrisonerData() {
    val prisonerData = listOf(
      PrisonerBuilder(alertCodes = listOf("A" to "AAR", "C" to "CC1")),
      PrisonerBuilder(alertCodes = listOf("A" to "AAR", "A" to "ADSC")),
      PrisonerBuilder(alertCodes = listOf("C" to "CC1", "C" to "CSIP")),
    )
    loadPrisonersFromBuilders(prisonerData)
  }

  @Test
  fun `should return alert types and codes`() {
    prisonApi.stubGetAlertTypes(alertTypes)

    referenceDataService.findAlertsReferenceData().apply {
      assertThat(this.alertTypes).containsExactlyInAnyOrderElementsOf(
        listOf(
          ReferenceDataAlertType(
            "A",
            "Social Care",
            true,
            listOf(
              ReferenceDataAlertCode("A", "AAR", "Adult At Risk (Home Office identified)", true),
              ReferenceDataAlertCode("A", "ADSC", "Adult Social Care", false),
            ),
          ),
          ReferenceDataAlertType(
            "C",
            "Child Communication Measures",
            true,
            listOf(
              ReferenceDataAlertCode("C", "CC1", "Child contact L1", true),
              ReferenceDataAlertCode("C", "CSIP", "CSIP", true),
            ),
          ),
        ),
      )
    }
  }

  val alertTypes = """
    [
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
          }
        ]
      },
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
      }
    ]
  """.trimIndent()
}
