package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import com.google.gson.Gson
import org.opensearch.OpenSearchStatusException
import org.opensearch.action.search.ClearScrollRequest
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.action.search.SearchScrollRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.common.unit.TimeValue
import org.opensearch.search.Scroll
import org.opensearch.search.builder.SearchSourceBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerLocation
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerLocationResponse

@Service
class PrisonerLocationService(
  private val searchClient: SearchClient,
  private val openSearchClient: RestHighLevelClient,
  private val gson: Gson,
  @Value("\${search.location.scroll-results}") private val results: Int = 1000,
  @Value("\${search.location.scroll-timeout-minutes}") private val timeout: Long = 5,
) {
  private val scroll = Scroll(TimeValue.timeValueMinutes(timeout))

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getAllPrisonerLocations(): PrisonerLocationResponse {
    val searchSourceBuilder = SearchSourceBuilder().apply {
      // restrict to just fetching the three required fields
      fetchSource(arrayOf("prisonerNumber", "prisonId", "lastPrisonId", "firstName", "lastName"), null)
      size(results)
    }
    val request = SearchRequest(arrayOf(PRISONER_INDEX), searchSourceBuilder).apply {
      scroll(scroll)
    }

    return processResponse(searchClient.search(request))
  }

  fun scrollPrisonerLocations(scrollId: String): PrisonerLocationResponse {
    val request = SearchScrollRequest(scrollId).apply {
      scroll(scroll)
    }
    return processResponse(scroll(request))
  }

  fun scroll(searchRequest: SearchScrollRequest): SearchResponse =
    try {
      openSearchClient.scroll(searchRequest, RequestOptions.DEFAULT)
    } catch (e: OpenSearchStatusException) {
      if (searchClient.isRetryable(e)) {
        log.warn("Retrying scroll", e)
        openSearchClient.scroll(searchRequest, RequestOptions.DEFAULT)
      } else {
        throw e
      }
    }

  private fun processResponse(
    response: SearchResponse,
  ): PrisonerLocationResponse {
    if (response.hits.hits.isEmpty() && response.scrollId != null) {
      openSearchClient.clearScroll(ClearScrollRequest().apply { addScrollId(response.scrollId) }, RequestOptions.DEFAULT)
      return PrisonerLocationResponse(null, emptyList())
    }

    return response.run {
      PrisonerLocationResponse(this.scrollId, this.hits.hits.map { gson.fromJson(it.sourceAsString, PrisonerLocation::class.java) })
    }
  }
}
