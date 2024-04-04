package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataService

class AlertsReferenceDataResourceTest : AbstractSearchIntegrationTest() {

  @Autowired
  private lateinit var referenceDataService: ReferenceDataService

  // TODO SDIT-1583 This test class will eventually be expanded to use the real endpoint and real alert codes including descriptions returned from prison-api. Just started with the OpenSearch bucket query for now.
  override fun loadPrisonerData() {
    val prisonerData = listOf(
      PrisonerBuilder(alertCodes = listOf("A" to "W", "B" to "Y")),
      PrisonerBuilder(alertCodes = listOf("A" to "W", "A" to "X")),
      PrisonerBuilder(alertCodes = listOf("B" to "Y", "B" to "Z")),
    )
    loadPrisonersFromBuilders(prisonerData)
  }

  @Test
  fun `should return buckets of alert types and codes`() {
    referenceDataService.findAlertsReferenceData().apply {
      assertThat(this).containsExactlyInAnyOrderEntriesOf(
        mapOf(
          "A" to listOf("W", "X"),
          "B" to listOf("Y", "Z"),
        ),
      )
    }
  }
}
