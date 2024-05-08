package uk.gov.justice.digital.hmpps.prisonersearch.common.dps

import java.time.LocalDate

data class RestrictedPatient(
  var supportingPrisonId: String?,
  val dischargedHospital: Agency?,
  val dischargeDate: LocalDate,
  val dischargeDetails: String?,
)
