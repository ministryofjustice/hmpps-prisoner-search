package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

import java.time.LocalDate

/**
 * Alias
 */
data class Alias(
  val title: String? = null,
  val firstName: String,
  val middleName: String? = null,
  val lastName: String,
  val age: Int? = null,
  val dob: LocalDate,
  val gender: String? = null,
  val ethnicity: String? = null,
  val raceCode: String? = null,
  val nameType: String? = null,
  val createDate: LocalDate,
  val offenderId: Long,
)
