package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

@Schema(
  description = """A matcher for an integer attribute from the Prisoner record.
  
  For a between clause use both min value and max value. By default the range is inclusive, but can be adjusted with minInclusive and maxInclusive.
  
  For <= enter only a max value, and for < set max inclusive to false.
   
  For >= enter only a min value, and for > set min inclusive to false.
  
  For equals enter the same integer in both the min value and max value and leave min/max inclusive as true.
  """,
)
data class IntMatcher(
  @Schema(description = "The attribute to match on", example = "heightCentimetres")
  override val attribute: String,
  @Schema(description = "The minimum value to match on", example = "150")
  val minValue: Int? = null,
  @Schema(description = "Whether the minimum value is inclusive", defaultValue = "true")
  val minInclusive: Boolean = true,
  @Schema(description = "The maximum value to match on", example = "180")
  val maxValue: Int? = null,
  @Schema(description = "Whether the maximum value is inclusive", defaultValue = "true")
  val maxInclusive: Boolean = true,
) : TypeMatcher<Int> {
  override fun validate() {
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

  override fun buildQuery(attributes: Attributes) = attributes[attribute]?.let {
    when {
      minValue != null && maxValue != null -> {
        QueryBuilders.rangeQuery(it.openSearchName).from(minValue).includeLower(minInclusive).to(maxValue).includeUpper(maxInclusive)
      }
      minValue != null -> {
        QueryBuilders.rangeQuery(it.openSearchName).from(minValue).includeLower(minInclusive)
      }
      maxValue != null -> {
        QueryBuilders.rangeQuery(it.openSearchName).to(maxValue).includeUpper(maxInclusive)
      }
      else -> throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }
  } ?: throw AttributeSearchException("Attribute $attribute not recognised")

  override fun toString() = if (minValue != null && maxValue != null && minValue == maxValue) {
    "$attribute = $minValue"
  } else {
    val min = minValue?.let { attribute + if (minInclusive) " >= $minValue" else " > $minValue" } ?: ""
    val max = maxValue?.let { attribute + if (maxInclusive) " <= $maxValue" else " < $maxValue" } ?: ""
    val join = if (minValue !== null && maxValue != null) " AND " else ""
    if (join.isEmpty()) "$min$join$max" else "($min$join$max)"
  }
}
