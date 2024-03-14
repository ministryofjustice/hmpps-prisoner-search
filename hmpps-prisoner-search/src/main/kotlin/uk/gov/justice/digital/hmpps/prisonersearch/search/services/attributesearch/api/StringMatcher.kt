package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

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

  override fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> =
    attributes[attribute]?.let {
      when (condition) {
        StringCondition.IS -> QueryBuilders.termQuery(it.openSearchName, searchTerm).caseInsensitive(true)
        StringCondition.IS_NOT -> QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery(it.openSearchName, searchTerm).caseInsensitive(true))
        StringCondition.CONTAINS -> QueryBuilders.wildcardQuery(it.openSearchName, "*$searchTerm*").caseInsensitive(true)
      }
    } ?: throw AttributeSearchException("Attribute $attribute not recognised")

  override fun toString(): String {
    val condition = when (condition) {
      StringCondition.IS -> "="
      StringCondition.IS_NOT -> "!="
      StringCondition.CONTAINS -> "CONTAINS"
    }
    return "$attribute $condition $searchTerm"
  }
}

@Schema(description = "The condition to apply to the attribute")
enum class StringCondition {
  IS,
  IS_NOT,
  CONTAINS,
}
