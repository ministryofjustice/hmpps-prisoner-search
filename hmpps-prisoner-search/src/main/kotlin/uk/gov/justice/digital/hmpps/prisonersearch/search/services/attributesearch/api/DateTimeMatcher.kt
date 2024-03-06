package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.SECONDS

@Schema(
  description = """A matcher for a date time attribute from the Prisoner record.
  
  For a between clause use both the min and max values.
  
  For < enter only the max value.
  
  For > enter only the min value.
""",
)
data class DateTimeMatcher(
  @Schema(description = "The attribute to search on", example = "currentIncentive.dateTime")
  override val attribute: String,
  @Schema(description = "The minimum value to match", example = "2024-01-01T09:00:00Z")
  val minValue: LocalDateTime? = null,
  @Schema(description = "The maximum value to match", example = "2024-01-31T21:00:00Z")
  val maxValue: LocalDateTime? = null,
) : TypeMatcher<LocalDateTime> {
  @Schema(description = "Must be DateTime", example = "DateTime")
  override val type: String = "DateTime"

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

  override fun buildQuery(): AbstractQueryBuilder<*> {
    val min = minValue?.truncateToSeconds()
    val max = maxValue?.truncateToSeconds()
    return when {
      min != null && max != null -> {
        QueryBuilders.rangeQuery(attribute).from(min).to(max)
      }
      min != null -> {
        QueryBuilders.rangeQuery(attribute).from(min)
      }
      max != null -> {
        QueryBuilders.rangeQuery(attribute).to(max)
      }
      else -> throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }
  }

  private fun LocalDateTime.truncateToSeconds() =
    truncatedTo(SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

  override fun toString(): String {
    val min = minValue?.let { "$attribute > $minValue" } ?: ""
    val max = maxValue?.let { "$attribute < $maxValue" } ?: ""
    val join = if (minValue !== null && maxValue != null) " AND " else ""
    return if (join.isEmpty()) "$min$join$max" else "($min$join$max)"
  }
}
