package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis

data class ProfileInformation(
  val type: String,
  val question: String,
  val resultValue: String,
)
