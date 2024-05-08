package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

import java.math.BigDecimal

data class PhysicalAttributes(
  val gender: String?,
  val raceCode: String?,
  val ethnicity: String?,
  val heightFeet: Int?,
  val heightInches: Int?,
  val heightMetres: BigDecimal?,
  val heightCentimetres: Int?,
  val weightPounds: Int?,
  val weightKilograms: Int?,
)
