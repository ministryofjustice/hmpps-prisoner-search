package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema

data class Language(
  @Schema(description = "A LOV from domain LANG_TYPE", allowableValues = ["PRIM", "SEC", "PREF_SPEAK", "PREF_WRITE"])
  val type: String? = null,
  @Schema(description = "The actual language code, from domain LANG", example = "ENG")
  val code: String? = null,
  @Schema(
    description = """The level of reading skill, from domain LANG_SKILLS:
    |Y  Yes
    |A	Average
    |D	Dyslexia
    |G	Good
    |N	Nil
    |P	Poor
    |R	Refused""",
    allowableValues = ["Y", "N", "A", "D", "G", "P", "R"],
  )
  val readSkill: String? = null,
  @Schema(description = "The level of writing skill, see description for readSkill", allowableValues = ["Y", "N", "A", "D", "G", "P", "R"])
  val writeSkill: String? = null,
  @Schema(description = "The level of writing skill, see description for readSkill", allowableValues = ["Y", "N", "A", "D", "G", "P", "R"])
  val speakSkill: String? = null,
  @Schema(description = "Whether an interpreter requested")
  val interpreterRequested: Boolean? = null,
)
