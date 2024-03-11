package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.opensearch.action.search.SearchRequest
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.search.builder.SearchSourceBuilder
import org.opensearch.search.sort.FieldSortBuilder
import org.opensearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.AND
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR
import kotlin.reflect.KClass

@Component
class AttributeSearchService(
  private val attributes: Attributes,
  private val elasticsearchClient: SearchClient,
  private val mapper: ObjectMapper,
  private val telemetryClient: TelemetryClient,
) {

  fun search(request: AttributeSearchRequest, pageable: Pageable = Pageable.ofSize(10)): Page<Prisoner> {
    log.info("searchByAttributes called with request: $request, pageable: $pageable")
    telemetryClient.trackEvent("POSAttributeSearch", mapOf("query" to request.toString()), null)
    request.validate(attributes)
    return doSearch(request, pageable)
  }

  private fun doSearch(request: AttributeSearchRequest, pageable: Pageable): Page<Prisoner> =
    QueryBuilders.boolQuery().apply {
      request.queries.forEach {
        when (request.joinType) {
          AND -> must(it.buildQuery())
          OR -> should(it.buildQuery())
        }
      }
    }.search(pageable)

  private fun BoolQueryBuilder.search(pageable: Pageable): Page<Prisoner> {
    val searchSourceBuilder = pageable.searchSourceBuilder(this)
    val searchRequest = SearchRequest(arrayOf(PRISONER_INDEX), searchSourceBuilder)
    val searchResponse = elasticsearchClient.search(searchRequest)
    val results = searchResponse.hits.hits.asList().map { mapper.readValue(it.sourceAsString, Prisoner::class.java) }
    return PageImpl(results, pageable, searchResponse.hits.totalHits?.value ?: 0)
  }

  private fun Pageable.searchSourceBuilder(queryBuilder: BoolQueryBuilder): SearchSourceBuilder {
    val sortBuilders = sort.map {
      FieldSortBuilder(getSortableAttribute(it)).order(SortOrder.fromString(it.direction.name))
    }.toList()
    return SearchSourceBuilder().apply {
      query(queryBuilder)
      size(pageSize)
      from(offset.toInt())
      sortBuilders.takeIf { it.isNotEmpty() }
        ?.forEach { sort(it) }
        ?: sort("prisonerNumber")
    }
  }

  private fun getSortableAttribute(it: Sort.Order): String =
    attributes[it.property]
      ?.let { type -> it.property.openSearchName(type) }
      ?: throw AttributeSearchException("Sort attribute '${it.property}' not found")

  fun getAttributes() = attributes.map { it.key to it.value.toString().lastWord() }.toMap()

  private fun String.lastWord() = split(".").last()

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

internal fun String.openSearchName(type: KClass<*>): String = when {
  this == "prisonerNumber" -> "prisonerNumber"
  type == String::class -> "$this.keyword"
  else -> this
}

class AttributeSearchException(message: String) : ValidationException(message)
