package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import java.util.concurrent.TimeUnit

@Service
class ReferenceDataService(
  private val elasticsearchClient: SearchClient,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  // we have implemented a cache loader in CacheConfig so that will call the findReferenceData method below
  @Cacheable(cacheNames = ["referenceData"], key = "#attribute")
  fun findReferenceDataCached(attribute: ReferenceDataAttribute) = ReferenceDataResponse()

  fun findReferenceData(attribute: ReferenceDataAttribute): ReferenceDataResponse {
    val searchSourceBuilder = createSourceBuilder(attribute)
    val searchRequest = SearchRequest(arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX), searchSourceBuilder)

    val searchResponse = elasticsearchClient.search(searchRequest)
    val aggregation: MultiBucketsAggregation = searchResponse.aggregations.get(attribute.name)
    return ReferenceDataResponse(
      aggregation.buckets.map {
        ReferenceData(it.keyAsString)
      }.sortedBy { it.key },
    )
  }

  private fun createSourceBuilder(attribute: ReferenceDataAttribute): SearchSourceBuilder =
    SearchSourceBuilder().apply {
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
enum class ReferenceDataAttribute(keyword: Boolean = true, field: String? = null) {
  build,
  category,
  csra,
  ethnicity,
  facialHair,
  gender,
  hairColour,
  imprisonmentStatusDescription,
  incentiveLevel(field = "currentIncentive.level.description.keyword"),
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

  val field: String = when {
    field != null -> field
    keyword -> "$name.keyword"
    else -> name
  }
}
