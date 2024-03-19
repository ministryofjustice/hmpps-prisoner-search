package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.opensearch.search.builder.SearchSourceBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration
import java.util.concurrent.TimeUnit

@Service
class ReferenceDataService(
  private val elasticsearchClient: SearchClient,
  @Value("\${search.reference.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.reference.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  // we have implemented a cache loader in CacheConfig so that will call the findReferenceData method below
  @Cacheable(cacheNames = ["referenceData"], key = "#attribute")
  fun findReferenceDataCached(attribute: ReferenceDataAttribute) = ReferenceDataResponse()

  fun findReferenceData(attribute: ReferenceDataAttribute): ReferenceDataResponse {
    val searchSourceBuilder = createSourceBuilder(attribute)
    val searchRequest = SearchRequest(arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX), searchSourceBuilder)

    val searchResponse = elasticsearchClient.search(searchRequest)
    val aggregation: MultiBucketsAggregation = searchResponse.aggregations.get(attribute.name)
    return aggregation.buckets.map {
      val key = it.keyAsString
      ReferenceData(value = key, label = attribute.map?.get(key) ?: key)
    }.sortedBy { it.label }.let {
      ReferenceDataResponse(it)
    }
  }

  private fun createSourceBuilder(attribute: ReferenceDataAttribute): SearchSourceBuilder =
    SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(0)
      aggregation(TermsAggregationBuilder(attribute.name).size(maxSearchResults).field(attribute.field))
    }
}

data class ReferenceDataResponse(val data: List<ReferenceData> = emptyList())

data class ReferenceData(val value: String, val label: String)

@Suppress("EnumEntryName")
enum class ReferenceDataAttribute(keyword: Boolean = true, field: String? = null, val map: Map<String, String>? = null) {
  build,
  category,
  csra,
  ethnicity,
  facialHair,
  gender,
  hairColour,
  imprisonmentStatusDescription,
  incentiveLevel(field = "currentIncentive.level.description.keyword"),
  inOutStatus(
    // OFFENDER_BOOKINGS.IN_OUT_STATUS column comment has this as reference code IN_OUT_STS, but that doesn't exist
    map = mapOf(
      "IN" to "Inside",
      "OUT" to "Outside",
      "TRN" to "Transfer",
    ),
  ),
  leftEyeColour,
  legalStatus(
    // this is an enum in Prison API anyway
    map = mapOf(
      "RECALL" to "Recall",
      "DEAD" to "Dead",
      "INDETERMINATE_SENTENCE" to "Indeterminate Sentence",
      "SENTENCED" to "Sentenced",
      "CONVICTED_UNSENTENCED" to "Convicted Unsentenced",
      "CIVIL_PRISONER" to "Civil Prisoner",
      "IMMIGRATION_DETAINEE" to "Immigration Detainee",
      "REMAND" to "Remand",
      "UNKNOWN" to "Unknown",
      "OTHER" to "Other",
    ),
  ),
  marksBodyPart(field = "marks.bodyPart.keyword"),
  maritalStatus,
  nationality,
  religion,
  rightEyeColour,
  scarsBodyPart(field = "scars.bodyPart.keyword"),
  shapeOfFace,
  status(
    keyword = false,
    // This is a joining in Prison API of the active flag with inOutStatus
    map = mapOf(
      "ACTIVE IN" to "Active Inside",
      "ACTIVE OUT" to "Active Outside",
      "INACTIVE OUT" to "Inactive Outside",
      "INACTIVE TRN" to "Inactive Transfer",
    ),
  ),
  tattoosBodyPart(field = "tattoos.bodyPart.keyword"),
  youthOffender(keyword = false, map = mapOf("true" to "Yes", "false" to "No")),
  ;

  val field: String = when {
    field != null -> field
    keyword -> "$name.keyword"
    else -> name
  }
}
