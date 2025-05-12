package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

data class OffenderLanguageDto(
  val type: String,
  val code: String,
  val readSkill: String,
  val writeSkill: String,
  val speakSkill: String,
  val interpreterRequested: Boolean,
)
