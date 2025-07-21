package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis

import java.time.LocalDate

data class PersonalCareNeedDto(
  val problemType: String,
  val problemCode: String,
  val problemStatus: String,
  val problemDescription: String? = null,
  val commentText: String? = null,
  val startDate: LocalDate,
  val endDate: LocalDate? = null,
)
