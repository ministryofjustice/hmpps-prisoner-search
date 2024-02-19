package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

data class Matchers(
  val joinType: JoinType,
  val matchers: List<TypeMatcher<*>>? = null,
  val children: List<Matchers>? = null,
) {
  fun validate() {
    if (typeMatchers().isEmpty() && children.isNullOrEmpty()) {
      throw AttributeSearchException("Matchers must not be empty")
    }
  }

  fun typeMatchers(): List<TypeMatcher<*>> =
//    listOfNotNull(stringMatchers, booleanMatchers, intMatchers, dateMatchers, dateTimeMatchers)
//      .flatten()
    matchers ?: emptyList()
}

fun List<Matchers>.getAllMatchers(): List<Matchers> {
  val allMatchers = mutableListOf<Matchers>()
  forEach {
    allMatchers.add(it)
    it.children?.also { children -> allMatchers.addAll(children.getAllMatchers()) }
  }
  return allMatchers
}

fun List<Matchers>.getAllTypeMatchers(): List<TypeMatcher<*>> {
  val allMatchers = mutableListOf<TypeMatcher<*>>()
  forEach {
    allMatchers.addAll(it.typeMatchers())
    it.children?.also { children -> allMatchers.addAll(children.getAllTypeMatchers()) }
  }
  return allMatchers
}

enum class JoinType {
  AND,
  OR,
}
