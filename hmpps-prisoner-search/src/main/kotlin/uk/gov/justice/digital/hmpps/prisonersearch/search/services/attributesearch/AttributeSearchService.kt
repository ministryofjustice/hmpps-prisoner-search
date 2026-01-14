package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
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
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Attribute
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.AND
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.OR
import kotlin.reflect.KClass

@Component
class AttributeSearchService(
  private val attributes: Attributes,
  private val elasticsearchClient: SearchClient,
  private val mapper: JsonMapper,
  private val responseFieldsValidator: ResponseFieldsValidator,
  private val telemetryClient: TelemetryClient,
) {

  fun search(request: AttributeSearchRequest, pageable: Pageable = Pageable.ofSize(10), responseFields: List<String>? = null): Page<Prisoner> {
    log.info("searchByAttributes called with request: $request, pageable: $pageable")
    responseFields?.run { responseFieldsValidator.validate(responseFields) }
    val telemetryMap = mutableMapOf("query" to request.toString(), "pageable" to pageable.toString())

    return try {
      request.validate(attributes)
      buildQuery(request)
        .search(pageable, telemetryMap, responseFields)
        .respond(pageable)
        .also { telemetryClient.trackEvent("POSAttributeSearch", telemetryMap, null) }
    } catch (e: Exception) {
      telemetryMap["error"] = e.message.toString()
      telemetryClient.trackEvent("POSAttributeSearchError", telemetryMap, null)
      throw e
    }
  }

  private fun buildQuery(request: AttributeSearchRequest): BoolQueryBuilder = QueryBuilders.boolQuery().apply {
    request.queries.forEach {
      when (request.joinType) {
        AND -> must(it.buildQuery(attributes))
        OR -> should(it.buildQuery(attributes))
      }
    }
  }

  private fun BoolQueryBuilder.search(pageable: Pageable, telemetryMap: MutableMap<String, String>, responseFields: List<String>? = null): SearchResponse {
    val searchSourceBuilder = pageable.searchSourceBuilder(this, responseFields)
    val searchRequest = SearchRequest(elasticsearchClient.getAlias(), searchSourceBuilder)
    return elasticsearchClient.search(searchRequest)
      .also {
        telemetryMap["resultCount"] = it.hits.hits.size.toString()
        telemetryMap["totalHits"] = it.hits.totalHits?.toString()?.extractInt() ?: "0"
        telemetryMap["timeInMs"] = it.took?.toString()?.extractInt() ?: "0"
      }
  }

  private fun String.extractInt() = "\\d+".toRegex().find(this)?.value?.toIntOrNull()?.toString()

  private fun SearchResponse.respond(pageable: Pageable): Page<Prisoner> = hits.hits.asList().map { mapper.readValue<Prisoner>(it.sourceAsString) }
    .let { PageImpl(it, pageable, hits.totalHits?.value ?: 0) }

  private fun Pageable.searchSourceBuilder(queryBuilder: BoolQueryBuilder, responseFields: List<String>? = null): SearchSourceBuilder {
    val sortBuilders = sort.map {
      FieldSortBuilder(getSortableAttribute(it)).order(SortOrder.fromString(it.direction.name))
    }.toList()
    return SearchSourceBuilder().apply {
      query(queryBuilder)
      size(pageSize)
      from(offset.toInt())
      responseFields?.run { fetchSource(toTypedArray(), emptyArray()) }
      sortBuilders.takeIf { it.isNotEmpty() }
        ?.forEach { sort(it) }
        ?: sort("prisonerNumber")
    }
  }

  private fun getSortableAttribute(it: Sort.Order): String = attributes[it.property]
    ?.openSearchName
    ?: throw AttributeSearchException("Sort attribute '${it.property}' not found")

  fun getAttributes() = attributes.map { (name, attribute) ->
    Attribute(name, attribute.type.matcherType(), name.isFuzzyAttribute())
  }

  private fun KClass<*>.matcherType() = simpleName!!.replace("Local", "")

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
