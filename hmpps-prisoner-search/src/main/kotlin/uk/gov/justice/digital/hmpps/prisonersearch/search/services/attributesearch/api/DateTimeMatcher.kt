package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import java.time.LocalDateTime

data class DateTimeMatcher(
  override val attribute: String,
  val minValue: LocalDateTime? = null,
  val maxValue: LocalDateTime? = null,
) : TypeMatcher<LocalDateTime> {
  override fun validate() {
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
