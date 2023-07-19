package uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto

data class PhysicalCharacteristic(
  val type: String,
  val characteristic: String,
  val detail: String?,
  val imageId: Long?,
)
