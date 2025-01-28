package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

@Schema(description = "A matcher for a boolean attribute from the Prisoner record")
data class BooleanMatcher(
  @Schema(description = "The attribute to match", example = "recall")
  override val attribute: String,
  @Schema(description = "Whether the attribute must be true or false", example = "true")
  val condition: Boolean,
) : TypeMatcher<Boolean> {
  @Schema(description = "Must be Boolean", example = "Boolean")
  override val type: String = "Boolean"

  override fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> = attributes[attribute]?.let {
    QueryBuilders.termQuery(it.openSearchName, condition)
  } ?: throw AttributeSearchException("Attribute $attribute not recognised")

  override fun toString() = "$attribute = $condition"
}
