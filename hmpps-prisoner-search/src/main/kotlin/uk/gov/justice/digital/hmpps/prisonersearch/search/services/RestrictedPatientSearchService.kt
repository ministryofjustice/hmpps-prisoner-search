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
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder

@Service
class RestrictedPatientSearchService(
  private val searchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findBySearchCriteria(searchCriteria: RestrictedPatientSearchCriteria, pageAndSize: PaginationRequest, responseFields: List<String>? = null): Page<Prisoner> {
    val pageable = pageAndSize.toPageable()
    if (searchCriteria.isEmpty()) {
      queryBy(searchCriteria, pageable.pageSize, pageable.offset.toInt(), responseFields) { anyMatch() } onMatch {
        customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
        return PageImpl(it.matches, pageable, it.totalHits)
      }
    } else {
      if (searchCriteria.prisonerIdentifier != null) {
        queryBy(searchCriteria, pageable.pageSize, pageable.offset.toInt(), responseFields) { idMatch(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
      // second attempt - if prisoner identifier not specified or no matches found then try with name match
      if (!searchCriteria.isNameEmpty()) {
        queryBy(searchCriteria, pageable.pageSize, pageable.offset.toInt(), responseFields) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(searchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
    }
    customEventForFindBySearchCriteria(searchCriteria, 0)
    return PageImpl(listOf(), pageable, 0L)
  }

  private fun queryBy(
    searchCriteria: RestrictedPatientSearchCriteria,
    pageSize: Int,
    pageOffset: Int,
    responseFields: List<String>? = null,
    queryBuilder: (searchCriteria: RestrictedPatientSearchCriteria) -> BoolQueryBuilder,
  ): RestrictedPatientResult {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val query = queryBuilder(searchCriteria)
    val searchSourceBuilder = SearchSourceBuilder().apply {
      query(query.withDefaults(searchCriteria))
      size(pageSize)
      from(pageOffset)
      sort("prisonerNumber")
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
    }
    val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
    val searchResults = searchClient.search(searchRequest)
    val prisonerMatches = getSearchResult(searchResults)
    return if (prisonerMatches.isEmpty()) {
      RestrictedPatientResult.NoMatch
    } else {
      RestrictedPatientResult.Match(
        prisonerMatches,
        searchResults.hits.totalHits?.value ?: 0,
      )
    }
  }

  private fun idMatch(searchCriteria: RestrictedPatientSearchCriteria): BoolQueryBuilder {
    with(searchCriteria) {
      return QueryBuilders.boolQuery()
        .mustMultiMatchKeyword(
          prisonerIdentifier?.prisonerNumberOrCanonicalPNCNumber(),
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

  private fun nameMatchWithAliases(searchCriteria: RestrictedPatientSearchCriteria): BoolQueryBuilder {
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

  private fun anyMatch(): BoolQueryBuilder = QueryBuilders.boolQuery()

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("search found ${searchHits.size} hits")
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun customEventForFindBySearchCriteria(
    searchCriteria: RestrictedPatientSearchCriteria,
    numberOfResults: Int,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "lastname" to searchCriteria.lastName,
      "firstname" to searchCriteria.firstName,
      "prisonerIdentifier" to searchCriteria.prisonerIdentifier,
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindRestrictedPatientsByCriteria", propertiesMap, metricsMap)
  }
}

sealed class RestrictedPatientResult {
  object NoMatch : RestrictedPatientResult()
  data class Match(val matches: List<Prisoner>, val totalHits: Long) : RestrictedPatientResult()
}

inline infix fun RestrictedPatientResult.onMatch(matchFunction: (RestrictedPatientResult.Match) -> Nothing) = when (this) {
  is RestrictedPatientResult.NoMatch -> {}
  is RestrictedPatientResult.Match -> matchFunction(this)
}

private fun BoolQueryBuilder.withDefaults(searchCriteria: RestrictedPatientSearchCriteria): BoolQueryBuilder = this.must("restrictedPatient", true)
  .filterWhenPresent("supportingPrisonId", searchCriteria.supportingPrisonIds)
