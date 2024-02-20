package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

data class Query(
  val joinType: JoinType,
  val matchers: List<TypeMatcher<*>>? = null,
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

enum class JoinType {
  AND,
  OR,
}
