package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.common.unit.TimeValue
import org.opensearch.index.query.BoolQueryBuilder
import org.opensearch.index.query.QueryBuilder
import org.opensearch.index.query.QueryBuilders
import org.opensearch.index.query.QueryBuilders.boolQuery
import org.opensearch.index.query.QueryBuilders.rangeQuery
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PhysicalDetailRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.util.concurrent.TimeUnit

@Service
class PhysicalDetailService(
  private val elasticsearchClient: SearchClient,
  private val gson: Gson,
  private val telemetryClient: TelemetryClient,
  private val authenticationHolder: HmppsAuthenticationHolder,
  @Value("\${search.detailed.max-results}") private val maxSearchResults: Int = 200,
  @Value("\${search.detailed.timeout-seconds}") private val searchTimeoutSeconds: Long = 10L,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun validateDetailRequest(detailRequest: PhysicalDetailRequest): Unit = with(detailRequest) {
    if (prisonIds.isNullOrEmpty()) {
      throw BadRequestException("Please provide prison locations to filter by")
    }
    if (!cellLocationPrefix.isNullOrEmpty() && prisonIds.size > 1) {
      throw BadRequestException("Cell location prefix can only be used for when searching in one prison")
    }
    if ((minHeight ?: 0) < 0) throw BadRequestException("Minimum height cannot be less than 0")
    if ((maxHeight ?: 0) < 0) throw BadRequestException("Maximum height cannot be less than 0")
    if ((minHeight ?: 0) > (maxHeight ?: Int.MAX_VALUE)) {
      throw BadRequestException("Maximum height cannot be less than the minimum height")
    }
    if ((minWeight ?: 0) < 0) throw BadRequestException("Minimum weight cannot be less than 0")
    if ((maxWeight ?: 0) < 0) throw BadRequestException("Maximum weight cannot be less than 0")
    if ((minWeight ?: 0) > (maxWeight ?: Int.MAX_VALUE)) {
      throw BadRequestException("Maximum weight cannot be less than the minimum weight")
    }
    if ((minShoeSize ?: 0) < 0) throw BadRequestException("Minimum shoe size cannot be less than 0")
    if ((maxShoeSize ?: 0) < 0) throw BadRequestException("Maximum shoe size cannot be less than 0")
    if ((minShoeSize ?: 0) > (maxShoeSize ?: Int.MAX_VALUE)) {
      throw BadRequestException("Maximum shoe size cannot be less than the minimum shoe size")
    }
  }

  fun findByPhysicalDetail(detailRequest: PhysicalDetailRequest): Page<Prisoner> {
    log.info("Received physical detail search request {}", gson.toJson(detailRequest))

    validateDetailRequest(detailRequest)
    val searchSourceBuilder = createSourceBuilder(detailRequest)
    val searchRequest = SearchRequest(arrayOf(PRISONER_INDEX), searchSourceBuilder)

    // Useful for logging the JSON elastic search query that is executed
    // log.info("Detail search query JSON: {}", searchSourceBuilder.toString())

    return try {
      val searchResponse = elasticsearchClient.search(searchRequest)
      customEventForFindBySearchCriteria(detailRequest, searchResponse.hits.totalHits?.value ?: 0)
      createDetailResponse(detailRequest.pagination, searchResponse)
    } catch (e: Throwable) {
      log.error("Elastic search exception", e)
      val pageable = PageRequest.of(detailRequest.pagination.page, detailRequest.pagination.size)
      return PageImpl(emptyList(), pageable, 0L)
    }
  }

  private fun createSourceBuilder(detailRequest: PhysicalDetailRequest): SearchSourceBuilder {
    val pageable = PageRequest.of(detailRequest.pagination.page, detailRequest.pagination.size)
    return SearchSourceBuilder().apply {
      timeout(TimeValue(searchTimeoutSeconds, TimeUnit.SECONDS))
      size(pageable.pageSize.coerceAtMost(maxSearchResults))
      from(pageable.offset.toInt())
      sort("_score")
      sort("prisonerNumber")
      minScore(0.01f) // needed so we exclude prisoners who are just matched by prison / cell location
      trackTotalHits(true)
      query(buildDetailQuery(detailRequest))
    }
  }

  private fun buildDetailQuery(detailRequest: PhysicalDetailRequest): BoolQueryBuilder {
    val detailQuery = boolQuery()

    with(detailRequest) {
      // Filter by prison establishments provided - must match
      prisonIds.takeIf { !it.isNullOrEmpty() && it[0].isNotBlank() }?.let {
        // note that we use filter here as we don't want to influence the score
        detailQuery.filterWhenPresent("prisonId", it)
      }

      // and restrict to single cell location prefix - must match
      val singlePrisonId = prisonIds?.singleOrNull()
      // if specified single prison then restrict to cell location
      cellLocationPrefix.takeIf { singlePrisonId != null }?.removePrefix("$singlePrisonId-")
        // note that we use filter here as we don't want to influence the score
        ?.let { detailQuery.filter(QueryBuilders.prefixQuery("cellLocation.keyword", it)) }

      gender?.let {
        detailQuery.shouldOrMust(
          lenient,
          boolQuery()
            .should(QueryBuilders.matchPhraseQuery("gender", it))
            .should(QueryBuilders.matchPhraseQuery("aliases.gender", it)),
        )
      }
      ethnicity?.let {
        detailQuery.shouldOrMust(
          lenient,
          boolQuery()
            .should(QueryBuilders.matchPhraseQuery("ethnicity", it))
            .should(QueryBuilders.matchPhraseQuery("aliases.ethnicity", it)),
        )
      }

      minHeight?.let { detailQuery.shouldOrMust(lenient, rangeQuery("heightCentimetres").gte(it)) }
      maxHeight?.let { detailQuery.shouldOrMust(lenient, rangeQuery("heightCentimetres").lte(it)) }

      minWeight?.let { detailQuery.shouldOrMust(lenient, rangeQuery("weightKilograms").gte(it)) }
      maxWeight?.let { detailQuery.shouldOrMust(lenient, rangeQuery("weightKilograms").lte(it)) }

      hairColour?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("hairColour", it)) }
      rightEyeColour?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("rightEyeColour", it)) }
      leftEyeColour?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("leftEyeColour", it)) }
      facialHair?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("facialHair", it)) }
      shapeOfFace?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("shapeOfFace", it)) }
      build?.let { detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("build", it)) }

      minShoeSize?.let { detailQuery.shouldOrMust(lenient, rangeQuery("shoeSize").gte(it)) }
      maxShoeSize?.let { detailQuery.shouldOrMust(lenient, rangeQuery("shoeSize").lte(it)) }

      tattoos?.forEach { tattoo ->
        tattoo.bodyPart?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("tattoos.bodyPart", tattoo.bodyPart))
        }
        tattoo.comment?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("tattoos.comment", tattoo.comment))
        }
      }
      scars?.forEach { scar ->
        scar.bodyPart?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("scars.bodyPart", scar.bodyPart))
        }
        scar.comment?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("scars.comment", scar.comment))
        }
      }
      marks?.forEach { mark ->
        mark.bodyPart?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("marks.bodyPart", mark.bodyPart))
        }
        mark.comment?.let {
          detailQuery.shouldOrMust(lenient, QueryBuilders.matchPhraseQuery("marks.comment", mark.comment))
        }
      }
    }

    return detailQuery
  }

  private fun createDetailResponse(
    paginationRequest: PaginationRequest,
    searchResponse: SearchResponse,
  ): Page<Prisoner> {
    val pageable = PageRequest.of(paginationRequest.page, paginationRequest.size)
    val prisoners = getSearchResult(searchResponse)
    return if (prisoners.isEmpty()) {
      log.info("Physical detail search: No prisoner matched this request. Returning empty response.")
      PageImpl(emptyList(), pageable, 0L)
    } else {
      log.info("Physical detail search: Matches found. Page ${pageable.pageNumber} with ${prisoners.size} prisoners, totalHits ${searchResponse.hits.totalHits?.value}")
      return PageImpl(prisoners, pageable, searchResponse.hits.totalHits!!.value)
      // Useful when checking the content of test results
      // log.info("Response content = ${gson.toJson(response)}")
    }
  }

  private fun getSearchResult(response: SearchResponse): List<Prisoner> {
    val searchHits = response.hits.hits.asList()
    log.debug("Physical detail search: found {} hits", searchHits.size)
    // Useful to find out what the score of each result is
    // searchHits.forEach { log.debug("{} has score {}", it.id, it.score) }
    return searchHits.map { gson.fromJson(it.sourceAsString, Prisoner::class.java) }
  }

  private fun customEventForFindBySearchCriteria(
    detailRequest: PhysicalDetailRequest,
    numberOfResults: Long,
  ) {
    val propertiesMap = mapOf(
      "username" to authenticationHolder.username,
      "clientId" to authenticationHolder.clientId,
      "cellLocationPrefix" to detailRequest.cellLocationPrefix,
      "minHeight" to detailRequest.minHeight?.toString(),
      "maxHeight" to detailRequest.maxHeight?.toString(),
      "minWeight" to detailRequest.minWeight?.toString(),
      "maxWeight" to detailRequest.maxWeight?.toString(),
      "prisonIds" to detailRequest.prisonIds.toString(),
    )
    val metricsMap = mapOf(
      "numberOfResults" to numberOfResults.toDouble(),
    )
    telemetryClient.trackEvent("POSFindByPhysicalDetails", propertiesMap, metricsMap)
  }
}

private fun BoolQueryBuilder.shouldOrMust(lenient: Boolean, query: QueryBuilder) = if (lenient) this.should(query) else this.must(query)
