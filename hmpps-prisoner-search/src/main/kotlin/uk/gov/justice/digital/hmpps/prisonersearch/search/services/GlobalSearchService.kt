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
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.ResponseFieldsValidator
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder

@Service
class GlobalSearchService(
  private val searchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  private val responseFieldsValidator: ResponseFieldsValidator,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findByGlobalSearchCriteria(globalSearchCriteria: GlobalSearchCriteria, pageable: Pageable, responseFields: List<String>? = null): Page<Prisoner> {
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    validateSearchForm(globalSearchCriteria)
    if (globalSearchCriteria.prisonerIdentifier != null) {
      queryBy(globalSearchCriteria, pageable, responseFields) { idMatch(it) } onMatch {
        customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
        return PageImpl(it.matches, pageable, it.totalHits)
      }
    }
    if (!(globalSearchCriteria.firstName.isNullOrBlank() && globalSearchCriteria.lastName.isNullOrBlank())) {
      if (globalSearchCriteria.includeAliases) {
        queryBy(globalSearchCriteria, pageable, responseFields) { nameMatchWithAliases(it) } onMatch {
          customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      } else {
        queryBy(globalSearchCriteria, pageable, responseFields) { nameMatch(it) } onMatch {
          customEventForFindBySearchCriteria(globalSearchCriteria, it.matches.size)
          return PageImpl(it.matches, pageable, it.totalHits)
        }
      }
    }
    return PageImpl(listOf(), pageable, 0L)
  }

  private fun validateSearchForm(globalSearchCriteria: GlobalSearchCriteria) {
    if (!globalSearchCriteria.isValid()) {
      log.warn("Invalid search  - no criteria provided")
      throw BadRequestException("Invalid search  - please provide at least 1 search parameter")
    }
  }

  private fun queryBy(
    globalSearchCriteria: GlobalSearchCriteria,
    pageable: Pageable,
    responseFields: List<String>? = null,
    queryBuilder: (globalSearchCriteria: GlobalSearchCriteria) -> BoolQueryBuilder?,
  ): GlobalResult {
    val query = queryBuilder(globalSearchCriteria)
    return query?.let {
      val searchSourceBuilder = SearchSourceBuilder().apply {
        query(query.withDefaults(globalSearchCriteria))
        size(pageable.pageSize)
        from(pageable.offset.toInt())
        sort("_score")
        sort("prisonerNumber")
        trackTotalHits(true)
        responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      }

      val searchRequest = SearchRequest(searchClient.getAlias(), searchSourceBuilder)
      val searchResults = searchClient.search(searchRequest)
      val prisonerMatches = getSearchResult(searchResults)
      return if (prisonerMatches.isEmpty()) {
        GlobalResult.NoMatch
      } else {
        GlobalResult.Match(
          prisonerMatches,
          searchResults.hits.totalHits?.value ?: 0,
        )
      }
    } ?: GlobalResult.NoMatch
  }

  private fun idMatch(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder {
    with(globalSearchCriteria) {
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

  private fun nameMatch(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder? {
    with(globalSearchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresentGender("gender", gender?.value)
                .mustWhenPresent("dateOfBirth", dateOfBirth),
            ),
        )
    }
  }

  private fun nameMatchWithAliases(globalSearchCriteria: GlobalSearchCriteria): BoolQueryBuilder? {
    with(globalSearchCriteria) {
      return QueryBuilders.boolQuery()
        .must(
          QueryBuilders.boolQuery()
            .should(
              QueryBuilders.boolQuery()
                .mustWhenPresent("lastName", lastName)
                .mustWhenPresent("firstName", firstName)
                .mustWhenPresentGender("gender", gender?.value)
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
                      .mustWhenPresentGender("aliases.gender", gender?.value)
                      .mustWhenPresent("aliases.dateOfBirth", dateOfBirth),
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

  private fun customEventForFindBySearchCriteria(
    globalSearchCriteria: GlobalSearchCriteria,
    numberOfResults: Int,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "lastname" to globalSearchCriteria.lastName,
      "firstname" to globalSearchCriteria.firstName,
      "gender" to globalSearchCriteria.gender?.value,
      "prisonId" to globalSearchCriteria.location,
      "dateOfBirth" to globalSearchCriteria.dateOfBirth.toString(),
      "prisonerIdentifier" to globalSearchCriteria.prisonerIdentifier,
      "includeAliases" to globalSearchCriteria.includeAliases.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByCriteria", propertiesMap, metricsMap)
  }
}

sealed class GlobalResult {
  object NoMatch : GlobalResult()
  data class Match(val matches: List<Prisoner>, val totalHits: Long) : GlobalResult()
}

inline infix fun GlobalResult.onMatch(block: (GlobalResult.Match) -> Nothing) = when (this) {
  is GlobalResult.NoMatch -> {
  }

  is GlobalResult.Match -> block(this)
}

private fun BoolQueryBuilder.withDefaults(globalSearchCriteria: GlobalSearchCriteria) = when (globalSearchCriteria.location) {
  "IN" -> mustNotWhenPresent("prisonId", "OUT")
  "OUT" -> filterWhenPresent("prisonId", "OUT")
  else -> this
}
