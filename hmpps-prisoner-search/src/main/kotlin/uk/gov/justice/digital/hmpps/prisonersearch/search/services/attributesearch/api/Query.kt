package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import com.fasterxml.jackson.annotation.JsonBackReference
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

@Schema(description = "A query to search for prisoners by attributes")
data class Query(
  @Schema(description = "The type of join to use when combining the matchers and subQueries", example = "AND", defaultValue = "AND")
  val joinType: JoinType = JoinType.AND,
  @Schema(description = "Matchers that will be applied to this query")
  val matchers: List<TypeMatcher<*>>? = null,
  @Schema(description = "A list of sub-queries of type Query that will be combined with the matchers in this query")
  @JsonBackReference
  val subQueries: List<Query>? = null,
) {
  fun validate() {
    if (matchers.isNullOrEmpty() && subQueries.isNullOrEmpty()) {
      throw AttributeSearchException("Query must not be empty")
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
