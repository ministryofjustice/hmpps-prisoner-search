package uk.gov.justice.digital.hmpps.prisonersearch.common.dps

data class ComplexityOfNeeds(
  val offenderNo: String,
  val level: String,
  // ignoring this and other fields for now as the existing KW functionality doesn't use it
  val active: Boolean,
)
