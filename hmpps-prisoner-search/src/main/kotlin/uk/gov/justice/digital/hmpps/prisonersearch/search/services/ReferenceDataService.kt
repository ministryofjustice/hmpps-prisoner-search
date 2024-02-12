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
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.AttributeNotFoundException
import java.util.concurrent.TimeUnit

@Service
class ReferenceDataService(
  private val elasticsearchClient: SearchClient,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  fun findReferenceData(attribute: String): ReferenceDataResponse {
    val convertedAttribute = convertAndValidateAttribute(attribute)
    val searchSourceBuilder = createSourceBuilder(convertedAttribute)
    val searchRequest = SearchRequest(arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX), searchSourceBuilder)

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      val aggregation: MultiBucketsAggregation = searchResponse.aggregations.get(convertedAttribute)
      ReferenceDataResponse(
        aggregation.buckets.map {
          ReferenceData(it.keyAsString)
        },
      )
    } catch (e: Throwable) {
      log.error("Elastic search exception", e)
      return ReferenceDataResponse()
    }
  }

  private fun convertAndValidateAttribute(attribute: String): String = attributeMappings[attribute]
    ?: throw AttributeNotFoundException("No reference data mapping found for $attribute")

  private fun createSourceBuilder(attribute: String): SearchSourceBuilder = SearchSourceBuilder().apply {
    timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
    size(0)
    aggregation(TermsAggregationBuilder(attribute).size(maxSearchResults).field(attribute))
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val attributeMappings = mapOf(
      "build" to "build.keyword",
      "category" to "category.keyword",
      "csra" to "csra.keyword",
      "ethnicity" to "ethnicity.keyword",
      "facialHair" to "facialHair.keyword",
      "gender" to "gender.keyword",
      "hairColour" to "hairColour.keyword",
      "imprisonmentStatusDescription" to "imprisonmentStatusDescription.keyword",
      "inOutStatus" to "inOutStatus.keyword",
      "leftEyeColour" to "leftEyeColour.keyword",
      "legalStatus" to "legalStatus.keyword",
      "maritalStatus" to "maritalStatus.keyword",
      "nationality" to "nationality.keyword",
      "religion" to "religion.keyword",
      "rightEyeColour" to "rightEyeColour.keyword",
      "shapeOfFace" to "shapeOfFace.keyword",
      "shoeSize" to "shoeSize.keyword",
      "status" to "status",
      "youthOffender" to "youthOffender",
    )
  }
}

data class ReferenceDataResponse(val data: List<ReferenceData> = emptyList())
data class ReferenceData(val key: String)
