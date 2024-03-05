package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.opensearch.action.search.SearchRequest
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher

@Component
class AttributeSearchService(
  private val attributes: Attributes,
  private val elasticsearchClient: SearchClient,
  private val mapper: ObjectMapper,
  private val telemetryClient: TelemetryClient,
) {

  fun search(request: AttributeSearchRequest, pageable: Pageable = Pageable.unpaged()): Page<Prisoner> {
    log.info("searchByAttributes called with request: $request, pageable: $pageable")
    telemetryClient.trackEvent("POSAttributeSearch", mapOf("query" to request.toString()), null)
    request.validate(attributes)
    return doSearch(request, pageable)
  }

  private fun doSearch(request: AttributeSearchRequest, pageable: Pageable): Page<Prisoner> =
    request.queries[0].matchers!!
      .buildQuery(request.queries[0].joinType)
      .let { it.search(pageable) }

  private fun List<TypeMatcher<*>>.buildQuery(joinType: JoinType) =
    QueryBuilders.boolQuery()
      .apply {
        forEach {
          when (joinType) {
            JoinType.AND -> must(it.buildQuery())
            JoinType.OR -> should(it.buildQuery())
          }
        }
      }

  private fun BoolQueryBuilder.search(pageable: Pageable): Page<Prisoner> {
    val searchSourceBuilder = SearchSourceBuilder().query(this)
    val searchRequest = SearchRequest(arrayOf(PRISONER_INDEX), searchSourceBuilder)
    val searchResponse = elasticsearchClient.search(searchRequest)
    val results = searchResponse.hits.hits.asList().map { mapper.readValue(it.sourceAsString, Prisoner::class.java) }
    return PageImpl(results, pageable, searchResponse.hits.totalHits?.value ?: 0)
  }

  fun getAttributes() = attributes.map { it.key to it.value.toString().lastWord() }.toMap()

  private fun String.lastWord() = split(".").last()

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
