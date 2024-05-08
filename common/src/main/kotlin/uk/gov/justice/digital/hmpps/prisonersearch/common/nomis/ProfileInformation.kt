package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

data class ProfileInformation(
  val type: String,
  val question: String,
  val resultValue: String,
)
