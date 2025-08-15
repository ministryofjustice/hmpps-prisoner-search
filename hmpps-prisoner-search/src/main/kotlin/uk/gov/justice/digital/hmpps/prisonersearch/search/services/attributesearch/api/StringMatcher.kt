package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.common.unit.Fuzziness
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.CONTAINS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.IN
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
  private val listSearchTerms: List<String> = searchTerm.split(",").map { it.trim() }.filterNot { it.isEmpty() }

  override fun validate() {
    if (condition != IN && searchTerm.isBlank()) {
      throw AttributeSearchException("Attribute $attribute must not have a blank search term")
    }
    if (condition == IN && listSearchTerms.isEmpty()) {
      throw AttributeSearchException("Attribute $attribute must not have an empty list of search terms")
    }
  }

  override fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> = attributes[attribute]
    ?.let { attr ->
      val query = when (condition) {
        IS -> QueryBuilders.termQuery(attr.openSearchName, searchTerm).caseInsensitive(true)
        IS_NOT -> QueryBuilders.boolQuery().mustNot(QueryBuilders.termQuery(attr.openSearchName, searchTerm).caseInsensitive(true))
        CONTAINS -> {
          if (searchTerm.hasWildcard()) {
            QueryBuilders.wildcardQuery(attr.openSearchName, searchTerm).caseInsensitive(true)
          } else {
            QueryBuilders.wildcardQuery(attr.openSearchName, "*$searchTerm*").caseInsensitive(true)
          }
        }
        STARTSWITH -> QueryBuilders.prefixQuery(attr.openSearchName, searchTerm).caseInsensitive(true)
        IN -> QueryBuilders.termsQuery(attr.openSearchName, listSearchTerms)
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
    val conditionOutput = when (condition) {
      IS -> "="
      IS_NOT -> "!="
      CONTAINS -> "CONTAINS"
      STARTSWITH -> "STARTSWITH"
      IN -> "IN"
    }
    val searchTermOutput = if (condition == IN) "($searchTerm)" else searchTerm
    return "$attribute $conditionOutput $searchTermOutput"
  }
}

@Schema(
  description = """The condition to apply to the attribute value. 
    
  All String searches are case-insensitive.
  
  IS and IS_NOT require an exact match (wildcards ? and * will not work).
  
  For IS and CONTAINS some attributes support fuzzy matching e.g. they allow spelling mistakes. Call endpoint `/attribute-search/attributes` to see which attributes support fuzzy matching.
  
  CONTAINS without wildcards (? and *) for a non-fuzzy attribute looks for the exact search term anywhere in the attribute value.
  
  CONTAINS with wildcards ? (single character) and * (zero to many characters) perform a wildcard match which must match the entire attribute value.
  
  STARTSWITH checks only the prefix of the attribute value and does not support fuzzy matching or wildcards.
  
  IN checks a list of values for an exact match and does not support fuzzy matching, wildcards or case insensitive searching.
  """,
)
enum class StringCondition {
  IS,
  IS_NOT,
  CONTAINS,
  STARTSWITH,
  IN,
}
