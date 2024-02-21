package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

@Schema(description = "A query to search for prisoners by attributes")
data class Query(
  @Schema(description = "The type of join to use when combining the matchers and subQueries", example = "AND")
  val joinType: JoinType,
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

@Schema(description = "The type of join to use when combining the matchers and subQueries")
enum class JoinType {
  AND,
  OR,
}
