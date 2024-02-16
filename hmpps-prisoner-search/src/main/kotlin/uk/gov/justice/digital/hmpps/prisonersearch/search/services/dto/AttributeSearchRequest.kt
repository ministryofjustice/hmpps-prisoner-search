package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.AttributeType
import java.time.LocalDate
import java.time.LocalDateTime

data class AttributeSearchRequest(
  val matchers: List<Matchers>,
)

data class Matchers(
  val joinType: JoinType,
  val textMatchers: List<TextMatcher>? = null,
  val booleanMatchers: List<BooleanMatcher>? = null,
  val integerMatchers: List<IntegerMatcher>? = null,
  val dateMatchers: List<DateMatcher>? = null,
  val dateTimeMatchers: List<DateTimeMatcher>? = null,
  val children: List<Matchers>? = null,
)

interface TypeMatcher {
  val attribute: String
  fun validate(attributeTypes: Map<String, AttributeType>)
}

data class TextMatcher(
  override val attribute: String,
  val condition: TextCondition,
  val searchTerm: String,
) : TypeMatcher {
  override fun validate(attributeTypes: Map<String, AttributeType>) {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.STRING) {
          throw AttributeSearchException("Attribute $attribute is not a text attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (searchTerm.isBlank()) {
      throw AttributeSearchException("Attribute $attribute must not have a blank search term")
    }
  }
}

data class BooleanMatcher(
  override val attribute: String,
  val condition: Boolean,
) : TypeMatcher {
  override fun validate(attributeTypes: Map<String, AttributeType>) {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.BOOLEAN) {
          throw AttributeSearchException("Attribute $attribute is not a boolean attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")
  }
}

data class IntegerMatcher(
  override val attribute: String,
  val minValue: Int? = null,
  val minInclusive: Boolean = true,
  val maxValue: Int? = null,
  val maxInclusive: Boolean = true,
) : TypeMatcher {
  override fun validate(attributeTypes: Map<String, AttributeType>) {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.INTEGER) {
          throw AttributeSearchException("Attribute $attribute is not an integer attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (minValue == null && maxValue == null) {
      throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }

    if (minValue != null && maxValue != null) {
      val min = if (minInclusive) minValue else minValue + 1
      val max = if (maxInclusive) maxValue else maxValue - 1
      if (max < min) {
        throw AttributeSearchException("Attribute $attribute max value $max less than min value $min")
      }
    }
  }
}

data class DateMatcher(
  override val attribute: String,
  val minValue: LocalDate? = null,
  val minInclusive: Boolean = true,
  val maxValue: LocalDate? = null,
  val maxInclusive: Boolean = true,
) : TypeMatcher {
  override fun validate(attributeTypes: Map<String, AttributeType>) {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.DATE) {
          throw AttributeSearchException("Attribute $attribute is not a date attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (minValue == null && maxValue == null) {
      throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }

    if (minValue != null && maxValue != null) {
      val min = if (minInclusive) minValue else minValue.plusDays(1)
      val max = if (maxInclusive) maxValue else maxValue.minusDays(1)
      if (max < min) {
        throw AttributeSearchException("Attribute $attribute max value $max less than min value $min")
      }
    }
  }
}

data class DateTimeMatcher(
  override val attribute: String,
  val minValue: LocalDateTime? = null,
  val maxValue: LocalDateTime? = null,
) : TypeMatcher {
  override fun validate(attributeTypes: Map<String, AttributeType>) {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.DATE_TIME) {
          throw AttributeSearchException("Attribute $attribute is not a datetime attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (minValue == null && maxValue == null) {
      throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }

    if (minValue != null && maxValue != null) {
      if (maxValue < minValue) {
        throw AttributeSearchException("Attribute $attribute max value $maxValue less than min value $minValue")
      }
    }
  }
}

enum class JoinType {
  AND,
  OR,
}

enum class TextCondition {
  IS,
  IS_NOT,
  CONTAINS,
}
