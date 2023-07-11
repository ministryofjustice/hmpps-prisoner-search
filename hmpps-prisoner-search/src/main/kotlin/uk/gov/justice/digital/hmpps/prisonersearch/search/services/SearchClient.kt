package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.apache.http.client.config.RequestConfig
import org.opensearch.action.search.ClearScrollRequest
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.action.search.SearchScrollRequest
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestClient
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.core.CountResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Service

private const val ONE_MINUTE = 60000

@Service
class SearchClient(
  private val elasticsearchClient: RestHighLevelClient,
  @param:Qualifier("elasticsearchOperations") private val elasticsearchOperations: ElasticsearchOperations,
) {
  private val requestOptions =
    RequestOptions.DEFAULT.toBuilder().setRequestConfig(RequestConfig.custom().setSocketTimeout(ONE_MINUTE).build()).build()

  fun search(searchRequest: SearchRequest): SearchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT)
  fun count(countRequest: CountRequest): CountResponse = elasticsearchClient.count(countRequest, RequestOptions.DEFAULT)
  fun lowLevelClient(): RestClient = elasticsearchClient.lowLevelClient
  fun elasticsearchOperations(): ElasticsearchOperations = elasticsearchOperations
  fun scroll(searchScrollRequest: SearchScrollRequest): SearchResponse = elasticsearchClient.scroll(searchScrollRequest, requestOptions)
  fun clearScroll(clearScrollRequest: ClearScrollRequest) = elasticsearchClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT)
}
