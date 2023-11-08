package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.core.CountResponse
import org.springframework.stereotype.Service

@Service
class SearchClient(
  private val elasticsearchClient: RestHighLevelClient,
) {
  fun search(searchRequest: SearchRequest): SearchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
  fun count(countRequest: CountRequest): CountResponse = elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
}
