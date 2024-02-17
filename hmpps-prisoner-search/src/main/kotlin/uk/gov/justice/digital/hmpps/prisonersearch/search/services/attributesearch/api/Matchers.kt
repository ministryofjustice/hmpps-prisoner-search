package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType.BOOLEAN
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType.DATE
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType.DATE_TIME
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType.INTEGER
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType.STRING

data class Matchers(
  val joinType: JoinType,
  val stringMatchers: List<StringMatcher>? = null,
  val booleanMatchers: List<BooleanMatcher>? = null,
  val integerMatchers: List<IntegerMatcher>? = null,
  val dateMatchers: List<DateMatcher>? = null,
  val dateTimeMatchers: List<DateTimeMatcher>? = null,
  val children: List<Matchers>? = null,
) {
  fun validate() {
    if (stringMatchers.isNullOrEmpty() &&
      booleanMatchers.isNullOrEmpty() &&
      integerMatchers.isNullOrEmpty() &&
      dateMatchers.isNullOrEmpty() &&
      dateTimeMatchers.isNullOrEmpty() &&
      children.isNullOrEmpty()
    ) {
      throw AttributeSearchException("Matchers must not be empty")
    }
  }

  fun typeMatchers(type: AttributeType): List<TypeMatcher>? =
    when (type) {
      STRING -> stringMatchers
      BOOLEAN -> booleanMatchers
      INTEGER -> integerMatchers
      DATE -> dateMatchers
      DATE_TIME -> dateTimeMatchers
    }
}

fun List<Matchers>.getAllMatchers(): List<Matchers> {
  val allMatchers = mutableListOf<Matchers>()
  forEach {
    allMatchers.add(it)
    it.children?.also { children -> allMatchers.addAll(children.getAllMatchers()) }
  }
  return allMatchers
}

fun List<Matchers>.getAllMatchers(type: AttributeType): List<TypeMatcher> {
  val allMatchers = mutableListOf<TypeMatcher>()
  forEach {
    it.typeMatchers(type)?.also { typeMatchers -> allMatchers.addAll(typeMatchers) }
    it.children?.also { children -> allMatchers.addAll(children.getAllMatchers(type)) }
  }
  return allMatchers
}

enum class JoinType {
  AND,
  OR,
}
