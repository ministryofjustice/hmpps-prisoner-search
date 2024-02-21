package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import java.time.LocalDate

@Schema(
  description = """A matcher for a date attribute from the Prisoner record.
  
  For a between clause use both min value and max value. By default the range is inclusive, but can be adjusted with minInclusive and maxInclusive.
  
  For <= enter only a max value, and for < set max inclusive to false.
   
  For >= enter only a min value, and for > set min inclusive to false.
  
  For equals enter the same date in both the min value and max value and leave min/max inclusive as true.
  """,
)
data class DateMatcher(
  @Schema(description = "The attribute to match", example = "releaseDate")
  override val attribute: String,
  @Schema(description = "The minimum value to match", example = "2024-01-01")
  val minValue: LocalDate? = null,
  @Schema(description = "Whether the minimum value is inclusive or exclusive", defaultValue = "true")
  val minInclusive: Boolean = true,
  @Schema(description = "The maximum value to match", example = "2024-01-31")
  val maxValue: LocalDate? = null,
  @Schema(description = "Whether the maximum value is inclusive or exclusive", defaultValue = "true")
  val maxInclusive: Boolean = true,
) : TypeMatcher<LocalDate> {
  @Schema(description = "Must be Date", example = "Date")
  override val type: String = "Date"

  override fun validate() {
    if (minValue == null && maxValue == null) {
      throw AttributeSearchException("Attribute $attribute must have a min or max value")
    }

    if (minValue != null && maxValue != null) {
      val min = if (minInclusive) minValue else minValue.plusDays(1)
      val max = if (maxInclusive) maxValue else maxValue.minusDays(1)
      if (max < min) {
        throw AttributeSearchException("Attribute $attribute max value $max less than min value $min")
      }
    }
  }

  override fun toString() =
    if (minValue != null && maxValue != null && minValue == maxValue) {
      "$attribute = $minValue"
    } else {
      val min = minValue?.let { attribute + if (minInclusive) " >= $minValue" else " > $minValue" } ?: ""
      val max = maxValue?.let { attribute + if (maxInclusive) " <= $maxValue" else " < $maxValue" } ?: ""
      val join = if (minValue !== null && maxValue != null) " AND " else ""
      if (join.isEmpty()) "$min$join$max" else "($min$join$max)"
    }
}
