package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.common.unit.TimeValue
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PrisonerDetailRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.util.concurrent.TimeUnit

@Service
class PrisonerDetailService(
  private val elasticsearchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun validateDetailRequest(detailRequest: PrisonerDetailRequest) {
    if (detailRequest.prisonIds.isNullOrEmpty()) {
      throw ValidationException("Invalid prisoner detail search  - please provide prison locations to filter by")
    }
  }

  fun findByPrisonerDetail(detailRequest: PrisonerDetailRequest, responseFields: List<String>? = null): Page<Prisoner> {
    log.info("Received prisoner detail search request ${gson.toJson(detailRequest)}")

    validateDetailRequest(detailRequest)
    val searchSourceBuilder = createSourceBuilder(detailRequest, responseFields)
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    // log.info("Detail search query JSON: {}", searchSourceBuilder.toString())

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      customEventForFindBySearchCriteria(detailRequest, searchResponse.hits.totalHits?.value ?: 0)
      createDetailResponse(detailRequest.pagination, searchResponse)
    } catch (e: Throwable) {
      log.error("Elastic search exception", e)
      createEmptyDetailResponse(detailRequest.pagination)
    }
  }

  private fun createSourceBuilder(detailRequest: PrisonerDetailRequest, responseFields: List<String>? = null): SearchSourceBuilder {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val pageable = PageRequest.of(detailRequest.pagination.page, detailRequest.pagination.size)
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      sort("_score")
      sort("prisonerNumber")
      trackTotalHits(true)
      query(buildDetailQuery(detailRequest))
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
    }
  }

  private fun buildDetailQuery(detailRequest: PrisonerDetailRequest): BoolQueryBuilder {
    val detailQuery = QueryBuilders.boolQuery()

    if (noCriteriaProvided(detailRequest)) {
      detailQuery.should(QueryBuilders.matchAllQuery())
    }

    val fuzzyMatch = detailRequest.fuzzyMatch == true

    with(detailRequest) {
      // Match by firstName, exact or by wildcard and include aliases if set - reduce score for alias matches
      firstName.takeIf { !it.isNullOrBlank() }?.let {
        detailQuery.must(
          if (detailRequest.includeAliases) {
            QueryBuilders.boolQuery()
              .should(QueryBuilders.matchQuery("firstName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(5f))
              .should(QueryBuilders.matchQuery("aliases.firstName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(2f))
              .should(QueryBuilders.wildcardQuery("firstName", it.lowercase()).boost(5f))
              .should(QueryBuilders.wildcardQuery("aliases.firstName", it.lowercase()).boost(2f))
          } else {
            QueryBuilders.boolQuery()
              .should(QueryBuilders.matchQuery("firstName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(5f))
              .should(QueryBuilders.wildcardQuery("firstName", it.lowercase()).boost(5f))
          },
        )
      }

      // Match by lastName, exact or by wildcard match and include aliases - reduce score for alias matches
      lastName.takeIf { !it.isNullOrBlank() }?.let {
        detailQuery.must(
          if (detailRequest.includeAliases) {
            QueryBuilders.boolQuery()
              .should(QueryBuilders.matchQuery("lastName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(5f))
              .should(QueryBuilders.matchQuery("aliases.lastName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(2f))
              .should(QueryBuilders.wildcardQuery("lastName", it.lowercase()).boost(5f))
              .should(QueryBuilders.wildcardQuery("aliases.lastName", it.lowercase()).boost(2f))
          } else {
            QueryBuilders.boolQuery()
              .should(QueryBuilders.matchQuery("lastName", it.lowercase()).fuzzyTranspositions(fuzzyMatch).boost(5f))
              .should(QueryBuilders.wildcardQuery("lastName", it.lowercase()).boost(5f))
          },
        )
      }

      // Match by prisonerNumber, exact or by wildcard - reduce score for wildcard matches
      nomsNumber.takeIf { !it.isNullOrBlank() }?.let {
        detailQuery.must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.matchQuery("prisonerNumber", it.uppercase()).boost(5f))
            .should(QueryBuilders.wildcardQuery("prisonerNumber", it.uppercase()).boost(2f)),
        )
      }

      // Match by pncNumber, exact or by wildcard in all field variants - reduce score for wildcard matches
      pncNumber.takeIf { !it.isNullOrBlank() }?.let {
        detailQuery.must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.matchQuery("pncNumber", it.uppercase()).boost(5f))
            .should(QueryBuilders.matchQuery("pncNumberCanonicalLong", it.uppercase()).boost(5f))
            .should(QueryBuilders.matchQuery("pncNumberCanonicalShort", it.uppercase()).boost(5f))
            .should(QueryBuilders.wildcardQuery("pncNumber", it.uppercase()).boost(2f))
            .should(QueryBuilders.wildcardQuery("pncNumberCanonicalLong", it.uppercase()).boost(2f))
            .should(QueryBuilders.wildcardQuery("pncNumberCanonicalShort", it.uppercase()).boost(2f)),
        )
      }

      // Match by croNumber, exact or by wildcard - reduce score for wildcard matches
      croNumber.takeIf { !it.isNullOrBlank() }?.let {
        detailQuery.must(
          QueryBuilders.boolQuery()
            .should(QueryBuilders.matchQuery("croNumber", it.uppercase()).boost(5f))
            .should(QueryBuilders.wildcardQuery("croNumber", it.uppercase()).boost(2f)),
        )
      }

      // Filter by date of birth.
      detailQuery.mustWhenPresent("dateOfBirth", dateOfBirth)

      // Filter by prison establishments provided
      prisonIds.takeIf { it != null && it.isNotEmpty() && it[0].isNotBlank() }?.let {
        detailQuery.filterWhenPresent("prisonId", it)
      }
    }

    return detailQuery
  }

  private fun createDetailResponse(paginationRequest: PaginationRequest, searchResponse: SearchResponse): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Prisoner detail search: No prisoner matched this request. Returning empty response.")
      createEmptyDetailResponse(paginationRequest)
    } else {
      log.info("Prisoner detail search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
      val response = PageImpl(prisoners, pageable, searchResponse.hits.totalHits!!.value)
      // Useful when checking the content of test results
      // log.info("Response content = ${gson.toJson(response)}")
      response
    }
  }

  private fun createEmptyDetailResponse(paginationRequest: PaginationRequest): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    return PageImpl(emptyList(), pageable, 0L)
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("Prisoner detail search: found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun noCriteriaProvided(request: PrisonerDetailRequest): Boolean = request.firstName.isNullOrEmpty() &&
    request.lastName.isNullOrEmpty() &&
    request.nomsNumber.isNullOrEmpty() &&
    request.pncNumber.isNullOrEmpty() &&
    request.croNumber.isNullOrEmpty()

  private fun customEventForFindBySearchCriteria(
    detailRequest: PrisonerDetailRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "firstName" to detailRequest.firstName,
      "lastName" to detailRequest.lastName,
      "nomsNumber" to detailRequest.nomsNumber,
      "pncNumber" to detailRequest.pncNumber,
      "croNumber" to detailRequest.croNumber,
      "fuzzyMatch" to detailRequest.fuzzyMatch.toString(),
      "prisonIds" to detailRequest.prisonIds.toString(),
      "includeAliases" to detailRequest.includeAliases.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }
}
