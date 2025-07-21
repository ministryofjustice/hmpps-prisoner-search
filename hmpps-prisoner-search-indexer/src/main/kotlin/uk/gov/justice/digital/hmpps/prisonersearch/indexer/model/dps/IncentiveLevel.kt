package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps

import java.time.LocalDate
import java.time.LocalDateTime

data class IncentiveLevel(
  val iepCode: String,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val nextReviewDate: LocalDate?,
)
