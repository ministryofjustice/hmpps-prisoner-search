package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import java.time.LocalDate

data class AttributeSearchRequest(
  val matchers: List<Matcher>,
)

data class Matcher(
  val joinType: JoinType,
  val textMatchers: List<TextMatcher>? = null,
  val booleanMatchers: List<BooleanMatcher>? = null,
  val integerMatchers: List<IntegerMatcher>? = null,
  val dateMatchers: List<DateMatcher>? = null,
  val children: List<Matcher>? = null,
)

data class TextMatcher(
  val attribute: String,
  val condition: TextCondition,
  val searchTerm: String,
)

data class BooleanMatcher(
  val attribute: String,
  val condition: Boolean,
)

data class IntegerMatcher(
  val attribute: String,
  val minValue: Int? = null,
  val minInclusive: Boolean = true,
  val maxValue: Int? = null,
  val maxInclusive: Boolean = true,
)

data class DateMatcher(
  val attribute: String,
  val minValue: LocalDate? = null,
  val minInclusive: Boolean = true,
  val maxValue: LocalDate? = null,
  val maxInclusive: Boolean = true,
)

enum class JoinType {
  AND,
  OR,
}

enum class TextCondition {
  IS,
  IS_NOT,
  CONTAINS,
}
