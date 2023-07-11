package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableAsync
class WebClientConfiguration(
  @Value("\${api.base.url.oauth}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {

  @Bean
  fun hmppsAuthHealthWebClient(): WebClient = healthWebClient(hmppsAuthBaseUri)

  private fun healthWebClient(url: String): WebClient = WebClient.builder()
    .baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(healthTimeout)))
    .build()
}
