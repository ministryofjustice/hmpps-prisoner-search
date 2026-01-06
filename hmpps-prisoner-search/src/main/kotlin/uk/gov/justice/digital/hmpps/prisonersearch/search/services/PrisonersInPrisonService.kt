package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.common.unit.TimeValue
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.MultiMatchQueryBuilder
import org.opensearch.index.query.Operator
import org.opensearch.index.query.QueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.sort.SortBuilders
import org.opensearch.search.sort.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PrisonersInPrisonRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.util.concurrent.TimeUnit

@Service
class PrisonersInPrisonService(
  private val elasticsearchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
  @Value("\${search.prisoner.max-results}") private val maxSearchResults: Int = 3000,
  @Value("\${search.keyword.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    fun mapSortField(sortField: String) = when (sortField) {
      "cellLocation" -> "cellLocation.keyword"
      "firstName" -> "firstName.keyword"
      "lastName" -> "lastName.keyword"
      else -> sortField
    }
  }

  fun search(prisonId: String, prisonerSearchRequest: PrisonersInPrisonRequest, responseFields: List<String>? = null): Page<Prisoner> {
    log.info("Received keyword request ${gson.toJson(prisonerSearchRequest)}")

    responseFields?.run { responseFieldsValidator.validate(responseFields) }

    val searchSourceBuilder = createSourceBuilder(prisonId, prisonerSearchRequest, responseFields)
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    log.info("search query JSON: {}", searchSourceBuilder.toString())

    val searchResponse = elasticsearchClient.search(searchRequest)
    return createSearchResponse(prisonerSearchRequest.pagination, searchResponse).also {
      auditSearch(prisonId, prisonerSearchRequest, searchResponse.hits.totalHits?.value ?: 0)
    }
  }

  private fun createSourceBuilder(
    prisonId: String,
    searchRequest: PrisonersInPrisonRequest,
    responseFields: List<String>? = null,
  ): SearchSourceBuilder {
    val pageable = searchRequest.pagination.toPageable()
    val sorting = searchRequest.sort.toList()
      .map { sort ->
        SortBuilders.fieldSort(mapSortField(sort.property)).order(
          when (sort.direction) {
            Sort.Direction.DESC -> SortOrder.DESC
            Sort.Direction.ASC -> SortOrder.ASC
            else -> throw IllegalArgumentException("Invalid sort direction: ${sort.direction}")
          },
        )
      }
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      trackTotalHits(true)
      query(buildKeywordQuery(prisonId, searchRequest))
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      // This was sort(sorting) - but no longer takes a list, so loop round
      for (fieldSortBuilder in sorting) {
        sort(fieldSortBuilder)
      }
    }
  }

  private fun buildKeywordQuery(prisonId: String, searchRequest: PrisonersInPrisonRequest): BoolQueryBuilder {
    val query = QueryBuilders.boolQuery()

    // Pattern match terms which might be NomsId, PNC or CRO & uppercase them
    val sanitisedSearchRequest = searchRequest.copy(term = convertTokensToSearchTerms(searchRequest.term))

    with(sanitisedSearchRequest) {
      term.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if any of the words specified match in any of the fields
        query.must().add(
          generateMatchQuery(it),
        )
      }

      query.filterWhenPresent("prisonId", prisonId)
      query.filterWhenPresent("alerts.alertCode", alertCodes)
      query.filterWhenPresent("currentIncentive.level.code", incentiveLevelCode)

      // when they are null ES will just ignore the range
      query.filter(QueryBuilders.rangeQuery("dateOfBirth").from(fromDob).to(toDob))
      cellLocationPrefix?.removePrefix("$prisonId-")
        ?.let { query.filter(QueryBuilders.prefixQuery("cellLocation.keyword", it)) }
    }

    return query
  }

  private fun generateMatchQuery(
    term: String,
  ): QueryBuilder {
    val fields = listOf(
      "prisonerNumber",
      "pncNumber",
      "pncNumberCanonicalShort",
      "pncNumberCanonicalLong",
      "croNumber",
      "bookNumber",
    )

    val nameFields = listOf(
      "firstName",
      "lastName",
      "firstName.keyword",
      "lastName.keyword",
    )

    val keywordQuery = QueryBuilders.multiMatchQuery(term, *fields.toTypedArray())
      .analyzer("whitespace")
      .lenient(true)
      .type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
      .operator(Operator.AND)

    val termsList = term.split("[\\s,;:+]".toRegex()).map { it.uppercase() }
    val prefixNameQuery = QueryBuilders.boolQuery().mustAll(
      termsList.map {
        QueryBuilders.boolQuery().shouldAll(nameFields.map { name -> QueryBuilders.prefixQuery(name, it) })
      },
    )

    val maybeWildcardNameQuery = termsList.takeIf { it.size > 1 }?.let {
      val wildCardNameTerm = termsList.joinToString("*") + "*"
      QueryBuilders.boolQuery().shouldAll(nameFields.map { QueryBuilders.wildcardQuery(it, wildCardNameTerm) })
    }

    return QueryBuilders.boolQuery().shouldAll(
      keywordQuery,
      prefixNameQuery,
      maybeWildcardNameQuery,
    )
  }

  private fun createSearchResponse(
    paginationRequest: PaginationRequest,
    searchResponse: SearchResponse,
  ): Page<Prisoner> {
    val pageable = paginationRequest.toPageable()
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Establishment search: No prisoner matched this request. Returning empty response.")
      createEmptyResponse(paginationRequest)
    } else {
      log.info("Establishment search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
      val response = PageImpl(prisoners, pageable, searchResponse.hits.totalHits!!.value)
      // Useful when checking the content of test results
      // log.info("Response content = ${gson.toJson(response)}")
      response
    }
  }

  private fun createEmptyResponse(paginationRequest: PaginationRequest): Page<Prisoner> {
    val pageable = paginationRequest.toPageable()
    return PageImpl(emptyList(), pageable, 0L)
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("Keyword search: found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun auditSearch(
    prisonId: String,
    searchRequest: PrisonersInPrisonRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to (authenticationHolder.username ?: "anonymous"),
      "clientId" to (authenticationHolder.clientId),
      "term" to (searchRequest.term ?: ""),
      "cellLocationPrefix" to (searchRequest.cellLocationPrefix ?: ""),
      "prisonId" to prisonId,
      "alertCodes" to searchRequest.alertCodes.joinToString(","),
      "fromDob" to (searchRequest.fromDob?.toString() ?: ""),
      "toDob" to (searchRequest.toDob?.toString() ?: ""),
      "incentiveLevelCode" to (searchRequest.incentiveLevelCode ?: ""),
      "sort" to searchRequest.sort.toString(),
      "page" to searchRequest.pagination.page.toString(),
      "size" to searchRequest.pagination.size.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSPrisonersInPrison", propertiesMap, metricsMap)
  }

  private fun convertTokensToSearchTerms(tokens: String?): String? {
    if (tokens.isNullOrEmpty()) {
      return tokens
    }
    var newTokens = ""
    val arrayOfTokens = tokens.split("\\s+".toRegex())
    arrayOfTokens.forEach {
      // short circuit - ignore everything in this special case
      if (it.isPrisonerNumber()) {
        return it.uppercase()
      }
      // TODO not sure we need this below, why they ever search by CRO/PNC within an establishment??
      newTokens += if (it.isCroNumber() || it.isPncNumber()) {
        "${it.uppercase()} "
      } else {
        "${it.lowercase()} "
      }
    }
    return newTokens.trim()
  }

  private fun String.isPncNumber() = matches("^\\d{4}/([0-9]+)[a-zA-Z]$".toRegex()) || matches("^\\d{2}/([0-9]+)[a-zA-Z]$".toRegex())

  private fun String.isCroNumber() = matches("^([0-9]+)/([0-9]+)[a-zA-Z]$".toRegex())
}
