package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType

data class IntegerMatcher(
  override val attribute: String,
  val minValue: Int? = null,
  val minInclusive: Boolean = true,
  val maxValue: Int? = null,
  val maxInclusive: Boolean = true,
) : TypeMatcher {
  override fun validate(attributeType: AttributeType) {
    if (attributeType != AttributeType.INTEGER) {
      throw AttributeSearchException("Attribute $attribute is not an integer attribute")
    }

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