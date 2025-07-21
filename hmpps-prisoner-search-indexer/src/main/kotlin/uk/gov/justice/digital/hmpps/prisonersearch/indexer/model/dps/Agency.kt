package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps

data class Agency(
  val agencyId: String,
  val description: String? = null,
  val longDescription: String? = null,
  val agencyType: String,
  val active: Boolean,
)
