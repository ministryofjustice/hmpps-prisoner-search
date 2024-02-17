package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import java.time.LocalDate

data class DateMatcher(
  override val attribute: String,
  val minValue: LocalDate? = null,
  val minInclusive: Boolean = true,
  val maxValue: LocalDate? = null,
  val maxInclusive: Boolean = true,
) : TypeMatcher {
  override fun validate() {
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
