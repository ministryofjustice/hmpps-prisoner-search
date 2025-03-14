package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation
import org.opensearch.search.aggregations.bucket.nested.NestedAggregationBuilder
import org.opensearch.search.aggregations.bucket.nested.ParsedNested
import org.opensearch.search.aggregations.bucket.terms.ParsedStringTerms
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.opensearch.search.builder.SearchSourceBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import java.util.concurrent.TimeUnit

@Service
class ReferenceDataService(
  private val elasticsearchClient: SearchClient,
  private val prisonApiService: PrisonApiService,
  @Value("\${search.reference.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.reference.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  // we have implemented a cache loader in CacheConfig so that will call the findReferenceData method below
  @Cacheable(cacheNames = ["referenceData"], key = "#attribute")
  fun findReferenceDataCached(attribute: ReferenceDataAttribute) = ReferenceDataResponse()

  fun findReferenceData(attribute: ReferenceDataAttribute): ReferenceDataResponse {
    val searchSourceBuilder = createSourceBuilder(attribute)
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)

    val searchResponse = elasticsearchClient.search(searchRequest)
    val aggregation: MultiBucketsAggregation = searchResponse.aggregations.get(attribute.name)
    return aggregation.buckets.map {
      val key = it.keyAsString
      ReferenceData(value = key, label = attribute.map?.get(key) ?: key)
    }.sortedBy { it.label }.let {
      ReferenceDataResponse(it)
    }
  }

  private fun createSourceBuilder(attribute: ReferenceDataAttribute): SearchSourceBuilder = SearchSourceBuilder().apply {
    timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
    size(0)
    aggregation(TermsAggregationBuilder(attribute.name).size(maxSearchResults).field(attribute.field))
  }

  // we have implemented a cache loader in CacheConfig so that will call the findAlertsReferenceData method below
  @Cacheable(cacheNames = ["alertsReferenceData"])
  fun findAlertsReferenceDataCached() = ReferenceDataAlertsResponse()

  fun findAlertsReferenceData(): ReferenceDataAlertsResponse {
    val prisonApiAlerts = prisonApiService.getAllAlerts()
    return findSearchableAlertsReferenceData().map { type ->
      val alertType = prisonApiAlerts.find { alertType -> alertType.type == type.key }
      AlertType(
        type = type.key,
        description = alertType?.description ?: type.key,
        active = alertType?.active ?: false,
        codes = type.value.map { code ->
          val alertCode = alertType?.alertCodes?.find { alertCode -> alertCode.code == code }
          AlertCode(
            type = type.key,
            code = code,
            description = alertCode?.description ?: code,
            active = alertCode?.active ?: false,
          )
        }.sortedBy { alertCode -> alertCode.description },
      )
    }.sortedBy { alertType ->
      alertType.description
    }.let {
      ReferenceDataAlertsResponse(it)
    }
  }

  private fun findSearchableAlertsReferenceData(): Map<String, List<String>> {
    val aggs = TermsAggregationBuilder("alertTypes").size(maxSearchResults).field("alerts.alertType.keyword")
      .subAggregation(TermsAggregationBuilder("alertCodes").size(maxSearchResults).field("alerts.alertCode.keyword"))
    val searchSourceBuilder = SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(0)
      aggregation(NestedAggregationBuilder("alerts", "alerts").subAggregation(aggs))
    }
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)
    val searchResponse = elasticsearchClient.search(searchRequest)
    return searchResponse.unpackAlertBuckets()
  }

  private fun SearchResponse.unpackAlertBuckets() = ((aggregations.first() as ParsedNested).aggregations.first() as ParsedStringTerms).buckets.associate {
    it.key as String to (it.aggregations.first() as ParsedStringTerms).buckets.map { it.key as String }
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
  title,
  youthOffender(keyword = false, map = mapOf("true" to "Yes", "false" to "No")),
  ;

  val field: String = when {
    field != null -> field
    keyword -> "$name.keyword"
    else -> name
  }
}
data class ReferenceDataAlertsResponse(val alertTypes: List<AlertType> = emptyList())

data class AlertType(
  val type: String,
  val description: String,
  val active: Boolean,
  val codes: List<AlertCode>,
)

data class AlertCode(
  val type: String,
  val code: String,
  val description: String,
  val active: Boolean,
)
