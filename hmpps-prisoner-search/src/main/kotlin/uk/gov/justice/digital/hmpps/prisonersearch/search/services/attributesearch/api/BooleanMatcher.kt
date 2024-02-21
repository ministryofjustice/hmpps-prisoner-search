package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A matcher for a boolean attribute from the Prisoner record")
data class BooleanMatcher(
  @Schema(description = "The attribute to match", example = "recall")
  override val attribute: String,
  @Schema(description = "Whether the attribute must be true or false", example = "true")
  val condition: Boolean,
) : TypeMatcher<Boolean> {
  @Schema(description = "Must be Boolean", example = "Boolean")
  override val type: String = "Boolean"

  override fun toString() = "$attribute = $condition"
}
