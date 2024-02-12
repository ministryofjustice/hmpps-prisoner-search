package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import java.util.concurrent.TimeUnit

@Service
class ReferenceDataService(
  private val elasticsearchClient: SearchClient,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  fun findReferenceData(attribute: ReferenceDataAttribute): ReferenceDataResponse {
    val searchSourceBuilder = createSourceBuilder(attribute)
    val searchRequest = SearchRequest(arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX), searchSourceBuilder)

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      val aggregation: MultiBucketsAggregation = searchResponse.aggregations.get(attribute.name)
      ReferenceDataResponse(
        aggregation.buckets.map {
          ReferenceData(it.keyAsString)
        }.sortedBy { it.key },
      )
    } catch (e: Throwable) {
      log.error("Elastic search exception", e)
      return ReferenceDataResponse()
    }
  }

  private fun createSourceBuilder(attribute: ReferenceDataAttribute): SearchSourceBuilder = SearchSourceBuilder().apply {
    timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
    size(0)
    aggregation(TermsAggregationBuilder(attribute.name).size(maxSearchResults).field(attribute.field))
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class ReferenceDataResponse(val data: List<ReferenceData> = emptyList())
data class ReferenceData(val key: String)

@Suppress("EnumEntryName")
enum class ReferenceDataAttribute(keyword: Boolean = true) {
  build,
  category,
  csra,
  ethnicity,
  facialHair,
  gender,
  hairColour,
  imprisonmentStatusDescription,
  inOutStatus,
  leftEyeColour,
  legalStatus,
  maritalStatus,
  nationality,
  religion,
  rightEyeColour,
  shapeOfFace,
  status(keyword = false),
  youthOffender(keyword = false),
  ;

  val field: String = if (keyword) "$name.keyword" else name
}
