package uk.gov.justice.digital.hmpps.prisonersearch.common.services

import org.opensearch.OpenSearchStatusException
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.client.RequestOptions
import org.opensearch.client.ResponseException
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.core.CountResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration

@Service
class SearchClient(
  private val elasticsearchClient: RestHighLevelClient,
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun search(searchRequest: SearchRequest): SearchResponse = try {
    elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
  } catch (e: OpenSearchStatusException) {
    if (isRetryable(e)) {
      log.warn("Retrying search", e)
      elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
    } else {
      throw e
    }
  }

  fun count(countRequest: CountRequest): CountResponse = try {
    elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
  } catch (e: OpenSearchStatusException) {
    if (isRetryable(e)) {
      log.warn("Retrying count", e)
      elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
    } else {
      throw e
    }
  }

  fun isRetryable(e: OpenSearchStatusException): Boolean {
    val cause = e.cause
    if (cause !is ResponseException) return false
    if (cause.response == null) return false
    if (cause.response.statusLine == null) return false
    return cause.response.statusLine.statusCode == 502
  }

  fun getAlias(): Array<String> = arrayOf(OpenSearchIndexConfiguration.PRISONER_INDEX)
}
