package uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto

data class ProfileInformation(
  val type: String,
  val question: String,
  val resultValue: String,
)
