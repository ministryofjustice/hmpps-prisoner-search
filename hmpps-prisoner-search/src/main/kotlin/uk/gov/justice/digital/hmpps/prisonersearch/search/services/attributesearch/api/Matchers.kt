package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

data class Matchers(
  val joinType: JoinType,
  val matchers: List<TypeMatcher<*>>? = null,
  val children: List<Matchers>? = null,
) {
  fun validate() {
    if (matchers.isNullOrEmpty() && children.isNullOrEmpty()) {
      throw AttributeSearchException("Matchers must not be empty")
    }
  }
}

fun List<Matchers>.getAllMatchers(): List<Matchers> =
  fold<Matchers, MutableList<Matchers>>(mutableListOf()) { allMatchers, matcher ->
    allMatchers.apply {
      add(matcher)
      addAll(matcher.children?.getAllMatchers() ?: emptyList())
    }
  }.toList()

fun List<Matchers>.getAllTypeMatchers(): List<TypeMatcher<*>> =
  fold<Matchers, MutableList<TypeMatcher<*>>>(mutableListOf()) { allTypeMatchers, matcher ->
    allTypeMatchers.apply {
      addAll(matcher.matchers ?: emptyList())
      addAll(matcher.children?.getAllTypeMatchers() ?: emptyList())
    }
  }.toList()

enum class JoinType {
  AND,
  OR,
}
