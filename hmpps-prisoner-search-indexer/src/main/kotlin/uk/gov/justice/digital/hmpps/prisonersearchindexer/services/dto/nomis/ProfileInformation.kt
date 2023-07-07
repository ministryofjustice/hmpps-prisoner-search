package uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis

data class ProfileInformation(
  val type: String,
  val question: String,
  val resultValue: String,
)
