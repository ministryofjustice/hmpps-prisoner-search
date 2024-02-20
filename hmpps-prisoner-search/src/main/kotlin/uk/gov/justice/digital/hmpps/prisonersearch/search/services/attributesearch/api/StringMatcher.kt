package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

@Schema(description = "A matcher for a string attribute from the prisoner record")
data class StringMatcher(
  @Schema(description = "The attribute to match on", example = "aliases.lastName")
  override val attribute: String,
  @Schema(description = "The condition to apply to the attribute", example = "IS")
  val condition: StringCondition,
  @Schema(description = "The search term to apply to the attribute", example = "Smith")
  val searchTerm: String,
) : TypeMatcher<String> {
  @Schema(description = "Must be String", example = "String")
  override val type: String = "String"

  override fun validate() {
    if (searchTerm.isBlank()) {
      throw AttributeSearchException("Attribute $attribute must not have a blank search term")
    }
  }
}

@Schema(description = "The condition to apply to the attribute")
enum class StringCondition {
  IS,
  IS_NOT,
  CONTAINS,
}
