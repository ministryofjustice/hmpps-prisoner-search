package uk.gov.justice.digital.hmpps.prisonersearch.common.services

import org.apache.hc.core5.http.ProtocolVersion
import org.apache.hc.core5.http.message.RequestLine
import org.apache.hc.core5.http.message.StatusLine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.opensearch.OpenSearchStatusException
import org.opensearch.action.search.SearchRequest
import org.opensearch.action.search.SearchResponse
import org.opensearch.client.RequestOptions
import org.opensearch.client.Response
import org.opensearch.client.ResponseException
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.core.CountResponse
import org.opensearch.core.rest.RestStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration

class SearchClientTest {
  private val restHighLevelClient = mock<RestHighLevelClient>()
  private val searchClient = SearchClient(restHighLevelClient)
  private val searchRequest = SearchRequest()
  private val countRequest = CountRequest()
  private val searchResponse = mock<SearchResponse>()
  private val countResponse = mock<CountResponse>()
  private val protocolVersion = ProtocolVersion("HTTP", 1, 1)
  private val mockResponse = mock<Response>().apply {
    whenever(this.statusLine).thenReturn(StatusLine(protocolVersion, 502, "Bad Gateway"))
    whenever(this.requestLine).thenReturn(RequestLine("method", "url", protocolVersion))
  }
  private val openSearchStatusException = OpenSearchStatusException(
    "unable to proxy request",
    RestStatus.BAD_GATEWAY,
    ResponseException(mockResponse),
  )

  @Test
  fun `search succeeds`() {
    whenever(restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT)).thenReturn(searchResponse)

    assertThat(searchClient.search(searchRequest)).isSameAs(searchResponse)
  }

  @Test
  fun `count succeeds`() {
    whenever(restHighLevelClient.count(countRequest, RequestOptions.DEFAULT)).thenReturn(countResponse)

    assertThat(searchClient.count(countRequest)).isSameAs(countResponse)
  }

  @Test
  fun `search fails`() {
    whenever(restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT)).thenThrow(RuntimeException::class.java)

    assertThrows<RuntimeException> { searchClient.search(searchRequest) }
    verify(restHighLevelClient, times(1)).search(searchRequest, RequestOptions.DEFAULT)
  }

  @Test
  fun `count fails`() {
    whenever(restHighLevelClient.count(countRequest, RequestOptions.DEFAULT)).thenThrow(RuntimeException::class.java)

    assertThrows<RuntimeException> { searchClient.count(countRequest) }
    verify(restHighLevelClient, times(1)).count(countRequest, RequestOptions.DEFAULT)
  }

  @Test
  fun `search is retried`() {
    whenever(restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT))
      .thenThrow(openSearchStatusException)
      .thenReturn(searchResponse)

    assertThat(searchClient.search(searchRequest)).isSameAs(searchResponse)
    verify(restHighLevelClient, times(2)).search(searchRequest, RequestOptions.DEFAULT)
  }

  @Test
  fun `count is retried`() {
    whenever(restHighLevelClient.count(countRequest, RequestOptions.DEFAULT))
      .thenThrow(openSearchStatusException)
      .thenReturn(countResponse)

    assertThat(searchClient.count(countRequest)).isSameAs(countResponse)
    verify(restHighLevelClient, times(2)).count(countRequest, RequestOptions.DEFAULT)
  }

  @Test
  fun `index name is returned`() {
    val searchClient = SearchClient(restHighLevelClient)
    assertThat(searchClient.getAlias().first()).isSameAs(OpenSearchIndexConfiguration.PRISONER_INDEX)
  }
}
