package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.alerts-api}") val alertsApiBaseUri: String,
  @Value("\${api.base.url.hmpps-auth}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {
  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun alertsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(alertsApiBaseUri, healthTimeout)

  @Bean
  fun alertsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "alerts-api", url = alertsApiBaseUri, timeout)
}
