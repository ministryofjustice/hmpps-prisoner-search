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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.KeywordRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.SearchType
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.util.concurrent.TimeUnit

@Service
class KeywordService(
  private val elasticsearchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
  @Value($$"${search.keyword.max-results}") private val maxSearchResults: Int = 200,
  @Value($$"${search.keyword.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findByKeyword(keywordRequest: KeywordRequest, responseFields: List<String>? = null): Page<Prisoner> {
    log.info("Received keyword request ${gson.toJson(keywordRequest)}")

    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val searchSourceBuilder = createSourceBuilder(keywordRequest, responseFields)
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    // log.info("Keyword query JSON: {}", searchSourceBuilder.toString())

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      customEventForFindBySearchCriteria(keywordRequest, searchResponse.hits.totalHits?.value ?: 0)
      createKeywordResponse(keywordRequest.pagination, searchResponse)
    } catch (e: Throwable) {
      log.error("Elastic search exception: $e")
      createEmptyResponse(keywordRequest.pagination)
    }
  }

  private fun createSourceBuilder(keywordRequest: KeywordRequest, responseFields: List<String>? = null): SearchSourceBuilder {
    val pageable = keywordRequest.pagination.toPageable()
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      trackTotalHits(true)
      query(buildKeywordQuery(keywordRequest))
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      when (keywordRequest.type) {
        SearchType.DEFAULT -> {
          sort("_score")
          sort("prisonerNumber")
        }
        SearchType.ESTABLISHMENT -> {
          sort("lastName.keyword")
          sort("firstName.keyword")
          sort("prisonerNumber")
        }
      }
    }
  }

  private fun buildKeywordQuery(keywordRequest: KeywordRequest): BoolQueryBuilder {
    val keywordQuery = QueryBuilders.boolQuery()
    if (noKeyWordsSpecified(keywordRequest)) {
      keywordQuery.should(QueryBuilders.matchAllQuery())
    }

    // Pattern match terms which might be NomsId, PNC or CRO & uppercase them
    val sanitisedKeywordRequest = KeywordRequest(
      orWords = addUppercaseKeywordTokens(keywordRequest.orWords),
      andWords = addUppercaseKeywordTokens(keywordRequest.andWords),
      exactPhrase = addUppercaseKeywordTokens(keywordRequest.exactPhrase),
      notWords = addUppercaseKeywordTokens(keywordRequest.notWords),
      prisonIds = keywordRequest.prisonIds,
      fuzzyMatch = keywordRequest.fuzzyMatch ?: false,
      pagination = keywordRequest.pagination,
      gender = keywordRequest.gender,
      location = keywordRequest.location,
      dateOfBirth = keywordRequest.dateOfBirth,
    )

    with(sanitisedKeywordRequest) {
      andWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if all the words specified match in any of the fields
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.AND, MultiMatchQueryBuilder.Type.CROSS_FIELDS, keywordRequest.type),
        )
      }

      orWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will include the prisoner document if any of the words specified match in any of the fields
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.OR, MultiMatchQueryBuilder.Type.BEST_FIELDS, keywordRequest.type),
        )
      }

      notWords.takeIf { !it.isNullOrBlank() }?.let {
        // Will exclude the prisoners with any of these words matching anywhere in the document
        keywordQuery.mustNot(
          QueryBuilders.multiMatchQuery(it, "*", "aliases.*", "alerts.*")
            .lenient(true)
            .fuzzyTranspositions(false)
            .operator(Operator.OR),
        )
      }

      exactPhrase.takeIf { !it.isNullOrBlank() }?.let {
        // Will include prisoner where this exact phrase appears anywhere in the document
        keywordQuery.must().add(
          generateMatchQuery(it, fuzzyMatch!!, Operator.AND, MultiMatchQueryBuilder.Type.PHRASE, keywordRequest.type),
        )
      }

      prisonIds.takeIf { !it.isNullOrEmpty() && it[0].isNotBlank() }?.let {
        // Filter to return only those documents that contain the prison locations specified by the client
        keywordQuery.filterWhenPresent("prisonId", it)
      }

      dateOfBirth.takeIf { it != null }?.let {
        // Filter to return only those documents that match the date of birth specified by the client
        keywordQuery.filterWhenPresent("dateOfBirth", it)
      }

      gender.takeIf { it != null }?.let {
        // Filter to return only those documents that match the gender specified by the client
        keywordQuery.filterWhenPresent("gender", it.value)
      }

      location.takeIf { !it.isNullOrEmpty() }?.let {
        // Filter to return only those documents that match the location specified by the client
        when (it) {
          "IN" -> keywordQuery.mustNotWhenPresent("prisonId", "OUT")
          "OUT" -> keywordQuery.filterWhenPresent("prisonId", "OUT")
        }
      }
    }

    return keywordQuery
  }

  private fun generateMatchQuery(
    term: String,
    fuzzyMatch: Boolean,
    operator: Operator,
    multiMatchType: MultiMatchQueryBuilder.Type,
    searchType: SearchType,
  ): QueryBuilder {
    val fields = if (searchType == SearchType.ESTABLISHMENT) {
      // user research will probably show we really only need names and prisonNumber
      // for now provide a certain degree of backward compatibility
      listOf(
        "prisonerNumber",
        "pncNumber",
        "pncNumberCanonicalShort",
        "pncNumberCanonicalLong",
        "croNumber",
        "bookNumber",
        "firstName",
        "lastName",
      )
    } else {
      listOf("*", "aliases.*", "alerts.*")
    }
    return QueryBuilders.multiMatchQuery(term, *fields.toTypedArray())
      // Boost the scores for specific fields so real names and IDs are ranked higher than alias matches
      .analyzer("whitespace")
      .field("lastName", 10f)
      .field("firstName", 10f)
      .field("prisonerNumber", 10f)
      .field("pncNumber", 10f)
      .field("pncNumberCanonicalShort", 10f)
      .field("pncNumberCanonicalLong", 10f)
      .field("croNumber", 10f)
      .lenient(true)
      .type(multiMatchType)
      .fuzzyTranspositions(fuzzyMatch)
      .operator(operator)
  }

  private fun createKeywordResponse(paginationRequest: PaginationRequest, searchResponse: SearchResponse): Page<Prisoner> {
    val pageable = paginationRequest.toPageable()
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Keyword search: No prisoner matched this request. Returning empty response.")
      createEmptyResponse(paginationRequest)
    } else {
      log.info("Keyword search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
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

  private fun noKeyWordsSpecified(request: KeywordRequest): Boolean = request.andWords.isNullOrEmpty() &&
    request.exactPhrase.isNullOrEmpty() &&
    request.orWords.isNullOrEmpty() &&
    request.notWords.isNullOrEmpty()

  private fun customEventForFindBySearchCriteria(
    keywordRequest: KeywordRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "andWords" to keywordRequest.andWords,
      "orWords" to keywordRequest.orWords,
      "notWords" to keywordRequest.notWords,
      "exactPhrase" to keywordRequest.exactPhrase,
      "prisonIds" to keywordRequest.prisonIds.toString(),
      "fuzzyMatch" to keywordRequest.fuzzyMatch.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }

  /*
   ** Some fields are defined as @Keyword in the ES mapping annotations so will not match when the query
   ** tokens are provided in lower or mixed case. Detect these and replace with an uppercase variant.
   */

  private fun addUppercaseKeywordTokens(tokens: String?): String? {
    if (tokens.isNullOrEmpty()) {
      return tokens
    }
    var newTokens = ""
    val arrayOfTokens = tokens.split("\\s+".toRegex())
    arrayOfTokens.forEach {
      newTokens += if (it.isPrisonerNumber() || it.isCroNumber() || it.isPncNumber()) {
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
