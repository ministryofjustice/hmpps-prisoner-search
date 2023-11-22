package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import org.apache.http.HttpHost
import org.opensearch.client.RestClient
import org.opensearch.client.RestHighLevelClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenSearchConfiguration {
  @Value("\${opensearch.uris}")
  private val url: String? = null

  @Bean
  fun openSearchClient() = RestHighLevelClient(RestClient.builder(HttpHost.create(url)))
}
