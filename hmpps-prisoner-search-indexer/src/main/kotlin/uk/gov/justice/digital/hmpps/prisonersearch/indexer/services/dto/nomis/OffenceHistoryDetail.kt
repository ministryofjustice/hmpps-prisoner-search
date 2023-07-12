package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis

import java.time.LocalDate

data class OffenceHistoryDetail(
  val bookingId: Long,
  val offenceDate: LocalDate?,
  val offenceRangeDate: LocalDate?,
  val offenceDescription: String?,
  val offenceCode: String,
  val statuteCode: String?,
  val mostSerious: Boolean,
)
