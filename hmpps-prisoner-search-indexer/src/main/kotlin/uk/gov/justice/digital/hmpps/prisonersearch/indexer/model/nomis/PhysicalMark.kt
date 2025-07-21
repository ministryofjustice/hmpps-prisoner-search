package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis

data class PhysicalMark(
  val type: String,
  val side: String?,
  val bodyPart: String?,
  val orientation: String?,
  val comment: String?,
  val imageId: Long?,
)
