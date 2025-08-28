package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.lucene.search.join.ScoreMode
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.canonicalPNCNumber
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.BookingIds
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.PrisonerListCriteria.PrisonerNumbers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PossibleMatchCriteria
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder

@Service
class PrisonerSearchService(
  private val searchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val RESULT_HITS_MAX = 1000
  }

  fun findBySearchCriteria(searchCriteria: SearchCriteria, responseFields: List<String>? = null): List<Prisoner> {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    validateSearchForm(searchCriteria)
    if (searchCriteria.prisonerIdentifier != null) {
      queryBy(searchCriteria, responseFields) { idMatch(it) } onMatch {
        customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
        return it.matches
      }
    }
    if (!(searchCriteria.firstName.isNullOrBlank() && searchCriteria.lastName.isNullOrBlank())) {
      if (searchCriteria.includeAliases) {
        queryBy(searchCriteria, responseFields) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return it.matches
        }
      } else {
        queryBy(searchCriteria, responseFields) { nameMatch(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return it.matches
        }
      }
    }
    customEventForFindBySearchCriteria(searchCriteria, 0)
    return emptyList()
  }

  fun findPossibleMatchesBySearchCriteria(searchCriteria: PossibleMatchCriteria, responseFields: List<String>? = null): List<Prisoner> {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    if (!searchCriteria.isValid()) {
      with("Invalid search  - please provide at least 1 search parameter") {
        log.warn(this)
        throw BadRequestException(this)
      }
    }
    val result = mutableListOf<Prisoner>()
    if (searchCriteria.nomsNumber != null) {
      result += queryBy(searchCriteria.nomsNumber.uppercase(), responseFields) { fieldMatch("prisonerNumber", it) }.collect()
    }
    if (searchCriteria.pncNumber != null) {
      result += queryBy(searchCriteria.pncNumber, responseFields) { pncMatch(it) }.collect()
    }
    if (searchCriteria.lastName != null && searchCriteria.dateOfBirth != null) {
      result += queryBy(searchCriteria, responseFields) { nameMatchWithAliasesAndDob(it) }.collect()
    }
    return result.distinctBy { it.prisonerNumber }
  }

  fun findByReleaseDate(searchCriteria: ReleaseDateSearch, paginationRequest: PaginationRequest, responseFields: List<String>? = null): Page<Prisoner> {
    searchCriteria.validate()
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val pageable = paginationRequest.toPageable()

    queryBy(
      searchCriteria = searchCriteria,
      pageSize = pageable.pageSize,
      pageOffset = pageable.offset.toInt(),
      responseFields = responseFields,
    ) { releaseDateMatch(it) } onMatch {
      customEventForFindByReleaseDate(searchCriteria, it.matches.size)
      return PageImpl(it.matches, pageable, it.totalHits)
    }
    return PageImpl(listOf(), pageable, 0L)
  }

  fun findByPrison(
    prisonId: String,
    paginationRequest: PaginationRequest,
    includeRestrictedPatients: Boolean = false,
    responseFields: List<String>? = null,
  ): Page<Prisoner> {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val pageable = paginationRequest.toPageable()

    queryBy(
      prisonId = prisonId,
      pageSize = pageable.pageSize,
      pageOffset = pageable.offset.toInt(),
      responseFields = responseFields,
    ) { if (includeRestrictedPatients) includeRestricted(it) else locationMatch(it) } onMatch {
      customEventForFindByPrisonId(prisonId, it.matches.size)
      return PageImpl(it.matches, pageable, it.totalHits)
    }
    return PageImpl(listOf(), pageable, 0L)
  }

  private fun validateSearchForm(searchCriteria: SearchCriteria) {
    if (!searchCriteria.isValid()) {
      with("Invalid search  - please provide at least 1 search parameter") {
        log.warn(this)
        throw BadRequestException(this)
      }
    }
  }

  private fun queryBy(
    searchCriteria: SearchCriteria,
    responseFields: List<String>? = null,
    queryBuilder: (searchCriteria: SearchCriteria) -> BoolQueryBuilder,
  ): Result {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    return queryBuilder(searchCriteria).let { query ->
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults(searchCriteria))
        responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
        size(RESULT_HITS_MAX)
      }
      val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
      val prisonerMatches = getSearchResult(searchClient.search(searchRequest))
      if (prisonerMatches.isEmpty()) Result.NoMatch else Result.Match(prisonerMatches)
    }
  }

  private fun <T> queryBy(
    searchCriteria: T,
    responseFields: List<String>? = null,
    queryBuilder: (searchCriteria: T) -> BoolQueryBuilder,
  ): Result = queryBuilder(searchCriteria).let { query ->
    val searchSourceBuilder = SearchSourceBuilder().apply {
      size(RESULT_HITS_MAX)
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      query(query)
    }
    val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
    val prisonerMatches = getSearchResult(searchClient.search(searchRequest))
    if (prisonerMatches.isEmpty()) Result.NoMatch else Result.Match(prisonerMatches)
  }

  private fun queryBy(
    searchCriteria: ReleaseDateSearch,
    pageSize: Int,
    pageOffset: Int,
    responseFields: List<String>? = null,
    queryBuilder: (searchCriteria: ReleaseDateSearch) -> BoolQueryBuilder,
  ): GlobalResult = queryBuilder(searchCriteria).let { query ->
    val searchSourceBuilder = SearchSourceBuilder().apply {
      query(query)
      size(pageSize)
      from(pageOffset)
      sort("_score")
      sort("prisonerNumber")
      trackTotalHits(true)
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
    }
    val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
    val searchResults = searchClient.search(searchRequest)
    val prisonerMatches = getSearchResult(searchResults)
    if (prisonerMatches.isEmpty()) {
      GlobalResult.NoMatch
    } else {
      GlobalResult.Match(
        prisonerMatches,
        searchResults.hits.totalHits?.value ?: 0,
      )
    }
  }

  private fun queryBy(
    prisonId: String,
    pageSize: Int,
    pageOffset: Int,
    responseFields: List<String>? = null,
    queryBuilder: (prisonId: String) -> BoolQueryBuilder,
  ): GlobalResult = queryBuilder(prisonId).let { query ->
    val searchSourceBuilder = SearchSourceBuilder().apply {
      query(query)
      size(pageSize)
      from(pageOffset)
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      sort("_score")
      sort("prisonerNumber")
      trackTotalHits(true)
    }
    val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
    val searchResults = searchClient.search(searchRequest)
    val prisonerMatches = getSearchResult(searchResults)
    if (prisonerMatches.isEmpty()) {
      GlobalResult.NoMatch
    } else {
      GlobalResult.Match(
        prisonerMatches,
        searchResults.hits.totalHits?.value ?: 0,
      )
    }
  }

  private fun matchByIds(criteria: PrisonerListCriteria<Any>): BoolQueryBuilder = when (criteria) {
    is PrisonerNumbers -> shouldMatchOneOf("prisonerNumber", criteria.values())
    is BookingIds -> shouldMatchOneOf("bookingId", criteria.values())
  }

  private fun idMatch(searchCriteria: SearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          prisonerIdentifier?.canonicalPNCNumber(),
          "prisonerNumber",
          "bookingId",
          "pncNumber",
          "pncNumberCanonicalShort",
          "pncNumberCanonicalLong",
          "croNumber",
          "bookNumber",
        )
    }
  }

  private fun fieldMatch(field: String, value: String): BoolQueryBuilder = QueryBuilders.boolQuery().must(field, value)

  private fun pncMatch(pncNumber: String) = QueryBuilders.boolQuery()
    .mustMultiMatchKeyword(
      pncNumber.canonicalPNCNumber(),
      "pncNumber",
      "pncNumberCanonicalShort",
      "pncNumberCanonicalLong",
    )

  private fun releaseDateMatch(searchCriteria: ReleaseDateSearch): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .matchesDateRange(
          earliestReleaseDate,
          latestReleaseDate,
          "conditionalReleaseDate",
          "confirmedReleaseDate",
          "postRecallReleaseDate",
        )
        .filterWhenPresent("prisonId", searchCriteria.prisonIds?.toList())
    }
  }

  private fun includeRestricted(prisonId: String): BoolQueryBuilder = QueryBuilders.boolQuery()
    .mustMultiMatch(prisonId, "prisonId", "supportingPrisonId")

  private fun locationMatch(prisonId: String): BoolQueryBuilder = QueryBuilders.boolQuery().must("prisonId", prisonId)

  private fun nameMatch(searchCriteria: SearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName),
            ),
        )
    }
  }

  private fun nameMatchWithAliases(searchCriteria: SearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName),
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName),
                  ),
                ScoreMode.Max,
              ),
            ),
        )
    }
  }

  private fun nameMatchWithAliasesAndDob(searchCriteria: PossibleMatchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresent("dateOfBirth", dateOfBirth),
            )
            .should(
              QueryBuilders.nestedQuery(
                "aliases",
                QueryBuilders.boolQuery()
                  .should(
                    QueryBuilders.boolQuery()
                      .mustWhenPresent("aliases.lastName", lastName)
                      .mustWhenPresent("aliases.firstName", firstName)
                      .mustWhenPresent("dateOfBirth", dateOfBirth),
                  ),
                ScoreMode.Max,
              ),
            ),
        )
    }
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("search found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  fun findBy(criteria: PrisonerListCriteria<Any>, responseFields: List<String>? = null): List<Prisoner> {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }

    with(criteria) {
      queryBy(criteria, responseFields) { matchByIds(it) } onMatch {
        customEventForFindBy(type, values().size, it.matches.size)
        return it.matches
      }
      return emptyList()
    }
  }

  private fun customEventForFindBySearchCriteria(searchCriteria: SearchCriteria, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "lastname" to searchCriteria.lastName,
      "firstname" to searchCriteria.firstName,
      "prisonId" to searchCriteria.prisonIds.toString(),
      "prisonerIdentifier" to searchCriteria.prisonerIdentifier,
      "includeAliases" to searchCriteria.includeAliases.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }

  private fun customEventForFindByReleaseDate(searchCriteria: ReleaseDateSearch, numberOfResults: Int) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "earliestReleaseDate" to searchCriteria.earliestReleaseDate.toString(),
      "latestReleaseDateRange" to searchCriteria.latestReleaseDate.toString(),
      "prisonId" to searchCriteria.prisonIds.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByReleaseDate", propertiesMap, metricsMap)
  }

  private fun customEventForFindBy(type: String, prisonerListNumber: Int, numberOfResults: Int) {
    val logMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "numberOfPrisonerIds" to prisonerListNumber.toString(),
    )

    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByListOf$type", logMap, metricsMap)
  }

  private fun customEventForFindByPrisonId(
    prisonId: String,
    numberOfResults: Int,
  ) {
    val propertiesMap = mapOf(
      "prisonId" to prisonId,
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByPrisonId", propertiesMap, metricsMap)
  }
}

sealed class Result {
  object NoMatch : Result()
  data class Match(val matches: List<Prisoner>) : Result()
}

fun Result.collect() = when (this) {
  is Result.NoMatch -> emptyList<Prisoner>()
  is Result.Match -> matches
}

inline infix fun Result.onMatch(block: (Result.Match) -> Nothing) = when (this) {
  is Result.NoMatch -> {
  }
  is Result.Match -> block(this)
}

private fun BoolQueryBuilder.withDefaults(searchCriteria: SearchCriteria): BoolQueryBuilder = this
  .filterWhenPresent("prisonId", searchCriteria.prisonIds)
