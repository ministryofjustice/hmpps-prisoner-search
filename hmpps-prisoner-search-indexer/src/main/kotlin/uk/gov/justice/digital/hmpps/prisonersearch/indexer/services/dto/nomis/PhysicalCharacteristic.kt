package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis

data class PhysicalCharacteristic(
  val type: String,
  val characteristic: String,
  val detail: String?,
  val imageId: Long?,
)
