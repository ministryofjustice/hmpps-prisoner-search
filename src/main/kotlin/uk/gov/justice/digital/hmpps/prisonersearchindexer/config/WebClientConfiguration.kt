package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@EnableAsync
class WebClientConfiguration(
  @Value("\${api.base.url.prison-api}") val prisonApiBaseUri: String,
  @Value("\${api.base.url.incentives}") val incentivesBaseUri: String,
  @Value("\${api.base.url.restricted-patients}") val restrictedPatientBaseUrl: String,
  @Value("\${api.base.url.oauth}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {

  @Bean
  fun hmppsAuthHealthWebClient(): WebClient = healthWebClient(hmppsAuthBaseUri)

  @Bean
  fun prisonApiHealthWebClient(): WebClient = healthWebClient(prisonApiBaseUri)

  private fun healthWebClient(url: String): WebClient = WebClient.builder()
    .baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(healthTimeout)))
    .build()

  @Bean
  fun prisonApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("prison-api")
    }

    return webClientBuilder
      .baseUrl(prisonApiBaseUri)
      .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
      .filter(oauth2Client).build()
  }

  @Bean
  fun restrictedPatientsHealthWebClient(): WebClient = healthWebClient(restrictedPatientBaseUrl)

  @Bean
  fun restrictedPatientsWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("restricted-patients-api")
    }

    return webClientBuilder
      .baseUrl(restrictedPatientBaseUrl)
      .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
      .filter(oauth2Client).build()
  }

  @Bean
  fun incentivesHealthWebClient(): WebClient = healthWebClient(incentivesBaseUri)

  @Bean
  fun incentivesWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("incentives-api")
    }

    return webClientBuilder
      .baseUrl(incentivesBaseUri)
      .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
      .filter(oauth2Client)
      .build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService,
  ): OAuth2AuthorizedClientManager {
    val authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    return AuthorizedClientServiceOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    ).also { it.setAuthorizedClientProvider(authorizedClientProvider) }
  }
}
