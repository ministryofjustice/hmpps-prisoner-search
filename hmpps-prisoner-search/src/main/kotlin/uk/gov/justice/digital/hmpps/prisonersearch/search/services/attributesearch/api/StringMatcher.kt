package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.common.unit.Fuzziness
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.CONTAINS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.IS_NOT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.STARTSWITH

@Schema(description = "A matcher for a string attribute from the prisoner record")
data class StringMatcher(
  @Schema(description = "The attribute to match on", example = "aliases.lastName")
  override val attribute: String,
  @Schema(description = "The condition to apply to the attribute", example = "IS")
  val condition: StringCondition,
  @Schema(description = "The search term to apply to the attribute. Search terms are not case-sensitive.", example = "Smith")
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
    attributes[attribute]
      ?.let { attr ->
        val query = when (condition) {
          IS -> QueryBuilders.termQuery(attr.openSearchName, searchTerm).caseInsensitive(true)
          IS_NOT -> QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery(attr.openSearchName, searchTerm).caseInsensitive(true))
          CONTAINS -> QueryBuilders.wildcardQuery(attr.openSearchName, "*$searchTerm*").caseInsensitive(true)
          STARTSWITH -> QueryBuilders.wildcardQuery(attr.openSearchName, "$searchTerm*").caseInsensitive(true)
        }
        return when {
          attr.isFuzzy && condition == IS -> {
            QueryBuilders.boolQuery()
              .should(query)
              .should(QueryBuilders.fuzzyQuery(attr.openSearchName, searchTerm))
          }
          attr.isFuzzy && condition == CONTAINS && !searchTerm.hasWildcard() -> {
            QueryBuilders.boolQuery()
              .should(query)
              .should(QueryBuilders.matchQuery(attribute, searchTerm).fuzziness(Fuzziness.AUTO))
          }
          else -> query
        }
      } ?: throw AttributeSearchException("Attribute $attribute not recognised")

  private fun String.hasWildcard() = contains("?") || contains("*")

  override fun toString(): String {
    val condition = when (condition) {
      IS -> "="
      IS_NOT -> "!="
      CONTAINS -> "CONTAINS"
      STARTSWITH -> "STARTSWITH"
    }
    return "$attribute $condition $searchTerm"
  }
}

@Schema(
  description = """The condition to apply to the attribute. 
  
  IS and IS_NOT require an exact match (wildcards ? and * will not work).
  
  For IS and CONTAINS, if the attribute contains free text then the search will also perform a fuzzy (partial) match.
  
  CONTAINS with wildcards ? (single character) and * (zero to many characters) will not perform a fuzzy match.
  """,
)
enum class StringCondition {
  IS,
  IS_NOT,
  CONTAINS,
  STARTSWITH,
}
