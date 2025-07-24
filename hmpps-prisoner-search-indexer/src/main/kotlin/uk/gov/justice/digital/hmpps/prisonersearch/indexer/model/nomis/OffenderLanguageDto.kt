package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis

data class OffenderLanguageDto(
  val type: String,
  val code: String,
  val readSkill: String? = null,
  val writeSkill: String? = null,
  val speakSkill: String? = null,
  val interpreterRequested: Boolean,
)
