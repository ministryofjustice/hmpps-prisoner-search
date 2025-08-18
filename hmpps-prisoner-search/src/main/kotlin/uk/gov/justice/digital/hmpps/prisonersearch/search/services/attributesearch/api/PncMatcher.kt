package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import org.apache.lucene.search.join.ScoreMode
import org.opensearch.index.query.AbstractQueryBuilder
import org.opensearch.index.query.QueryBuilders
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.canonicalPNCNumberShort
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.isPNCNumber
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

@Schema(
  description = """A matcher for PNC numbers.
  
    This is required because PNC numbers come in various formats with 2/4 long years and with/without leading zeroes.
     
    This matcher will find the matching PNC regardless of which format is used.

  The type must be set to PNC for this matcher.
  """,
)
data class PncMatcher(
  @Schema(description = "The PNC number match", example = "24/123456H")
  val pncNumber: String,
) : Matcher {
  override fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> {
    val searchTerm = if (pncNumber.isPNCNumber()) pncNumber.canonicalPNCNumberShort() else pncNumber
    return QueryBuilders.nestedQuery(
      "identifiers",
      QueryBuilders.boolQuery()
        .must(QueryBuilders.termQuery("identifiers.type.keyword", "PNC"))
        .must(QueryBuilders.termQuery("identifiers.value.keyword", searchTerm)),
      ScoreMode.None,
    )
  }

  override fun toString() = "pncNumber = $pncNumber"
}
