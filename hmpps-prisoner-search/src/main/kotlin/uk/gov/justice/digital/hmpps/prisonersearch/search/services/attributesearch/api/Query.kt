package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.AND
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR

@Schema(description = "A query to search for prisoners by attributes")
data class Query(
  @Schema(description = "The type of join to use when combining the matchers and subQueries", example = "AND", defaultValue = "AND")
  val joinType: JoinType = AND,
  @Schema(description = "Matchers that will be applied to this query")
  val matchers: List<TypeMatcher<*>>? = null,
  @Schema(description = "A list of sub-queries of type Query that will be combined with the matchers in this query")
  val subQueries: List<Query>? = null,
) {
  fun validate() {
    if (matchers.isNullOrEmpty() && subQueries.isNullOrEmpty()) {
      throw AttributeSearchException("Query must not be empty")
    }
  }

  fun buildQuery(attributes: Attributes): BoolQueryBuilder =
    QueryBuilders.boolQuery()
      .apply {
        matchers?.forEach {
          when (joinType) {
            AND -> must(it.buildQuery(attributes))
            OR -> should(it.buildQuery(attributes))
          }
        }
        subQueries?.forEach {
          when (joinType) {
            AND -> must(it.buildQuery(attributes))
            OR -> should(it.buildQuery(attributes))
          }
        }
      }

  override fun toString(): String {
    val matchersString = matchers?.joinToString(" ${joinType.name} ") { it.toString() } ?: ""
    val subQueriesString = subQueries?.joinToString(" ${joinType.name} ") { "($it)" } ?: ""
    val join = if (matchersString.isNotEmpty() && subQueriesString.isNotEmpty()) " ${joinType.name} " else ""
    return "$matchersString$join$subQueriesString"
  }
}

fun List<Query>.getAllQueries(): List<Query> =
  fold<Query, MutableList<Query>>(mutableListOf()) { allMatchers, matcher ->
    allMatchers.apply {
      add(matcher)
      addAll(matcher.subQueries?.getAllQueries() ?: emptyList())
    }
  }.toList()

fun List<Query>.getAllTypeMatchers(): List<TypeMatcher<*>> =
  fold<Query, MutableList<TypeMatcher<*>>>(mutableListOf()) { allTypeMatchers, matcher ->
    allTypeMatchers.apply {
      addAll(matcher.matchers ?: emptyList())
      addAll(matcher.subQueries?.getAllTypeMatchers() ?: emptyList())
    }
  }.toList()
