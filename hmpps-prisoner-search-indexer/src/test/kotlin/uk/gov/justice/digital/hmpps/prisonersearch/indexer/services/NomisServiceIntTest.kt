package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenceHistoryDetail
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi

class NomisServiceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var nomisService: NomisService

  @Test
  fun `should return most serious offence with highest severity`() {
    val ob = PrisonerBuilder(
      bookingId = 12345,
      offenceHistory = listOf(
        // wrong booking Id
        anOffence(
          bookingId = 54321,
          offenceCode = "M1",
          mostSerious = true,
          offenceSeverityRanking = 1,
        ),
        // most serious is false
        anOffence(
          bookingId = 12345,
          offenceCode = "M2",
          mostSerious = false,
          offenceSeverityRanking = 2,
        ),
        // ranking is lower than for M4
        anOffence(
          bookingId = 12345,
          offenceCode = "M3",
          mostSerious = true,
          offenceSeverityRanking = 99,
        ),
        // So THIS is the most serious offence
        anOffence(
          bookingId = 12345,
          offenceCode = "M4",
          mostSerious = true,
          offenceSeverityRanking = 3,
        ),
      ),
    )
    prisonApi.stubGetOffender(ob)

    val offender = nomisService.getOffender(ob.prisonerNumber)

    assertThat(offender?.mostSeriousOffence).isEqualTo("M4")
  }
}

private fun anOffence(
  bookingId: Long,
  offenceCode: String,
  mostSerious: Boolean,
  offenceSeverityRanking: Int,
) = OffenceHistoryDetail(
  bookingId = bookingId,
  offenceCode = offenceCode,
  mostSerious = mostSerious,
  offenceSeverityRanking = offenceSeverityRanking,
  offenceDate = null,
  offenceRangeDate = null,
  offenceDescription = offenceCode,
  statuteCode = null,
)
