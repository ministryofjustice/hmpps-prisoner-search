package uk.gov.justice.digital.hmpps.prisonersearch.common.dps

import java.time.LocalDate
import java.time.LocalDateTime

data class IncentiveLevel(
  val iepCode: String,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val nextReviewDate: LocalDate?,
)
