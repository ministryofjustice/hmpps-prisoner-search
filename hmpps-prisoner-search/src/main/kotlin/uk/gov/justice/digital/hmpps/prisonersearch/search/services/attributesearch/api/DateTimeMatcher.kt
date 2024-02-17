package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType
import java.time.LocalDateTime

data class DateTimeMatcher(
  override val attribute: String,
  val minValue: LocalDateTime? = null,
  val maxValue: LocalDateTime? = null,
) : TypeMatcher {
  override fun validate(attributeType: AttributeType) {
    if (attributeType != AttributeType.DATE_TIME) {
      throw AttributeSearchException("Attribute $attribute is not a datetime attribute")
    }

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