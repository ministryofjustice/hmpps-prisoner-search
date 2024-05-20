package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "An attribute that can be searched for in a query")
data class Attribute(
  @Schema(description = "The name of the attribute to be used when searching", example = "firstName")
  val name: String,
  @Schema(description = "The type of the attribute (used to determine which matcher to use)", example = "String")
  val type: String,
  @Schema(description = "Whether the attribute search will be fuzzy. Generally applicable to String attributes containing free text such as names.", example = "true")
  val fuzzySearch: Boolean,
)
