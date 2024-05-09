package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

import java.time.LocalDate

/**
 * Alias
 */
data class Alias(
  val title: String?,
  val firstName: String,
  val middleName: String?,
  val lastName: String,
  val age: Int?,
  val dob: LocalDate,
  val gender: String?,
  val ethnicity: String?,
  val nameType: String?,
  val createDate: LocalDate,
)
