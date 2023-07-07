package uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis

data class PhysicalCharacteristic(
  val type: String,
  val characteristic: String,
  val detail: String?,
  val imageId: Long?,
)
