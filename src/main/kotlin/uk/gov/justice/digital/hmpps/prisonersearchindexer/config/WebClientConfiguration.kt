package uk.gov.justice.digital.hmpps.prisonersearchindexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableAsync
class WebClientConfiguration(
  @Value("\${api.base.url.prison-api}") val prisonApiBaseUri: String,
  @Value("\${api.base.url.incentives}") val incentivesBaseUri: String,
  @Value("\${api.base.url.restricted-patients}") val restrictedPatientBaseUrl: String,
  @Value("\${api.base.url.oauth}") val hmppsAuthBaseUri: String,
) {

  @Bean
  fun hmppsAuthHealthWebClient(): WebClient = WebClient.builder().baseUrl(hmppsAuthBaseUri).build()

  @Bean
  fun prisonApiHealthWebClient(): WebClient = WebClient.builder().baseUrl(prisonApiBaseUri).build()

  @Bean
  fun prisonApiWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("prison-api")
    }

    return webClientBuilder.baseUrl(prisonApiBaseUri).filter(oauth2Client).build()
  }

  @Bean
  fun restrictedPatientsHealthWebClient(): WebClient = WebClient.builder().baseUrl(restrictedPatientBaseUrl).build()

  @Bean
  fun restrictedPatientsWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("restricted-patients-api")
    }

    return webClientBuilder.baseUrl(restrictedPatientBaseUrl).filter(oauth2Client).build()
  }

  @Bean
  fun incentivesHealthWebClient(): WebClient = WebClient.builder().baseUrl(incentivesBaseUri).build()

  @Bean
  fun incentivesWebClient(
    authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    webClientBuilder: WebClient.Builder,
  ): WebClient {
    val oauth2Client = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
      it.setDefaultClientRegistrationId("incentives-api")
    }

    return webClientBuilder.baseUrl(incentivesBaseUri).filter(oauth2Client).build()
  }

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ReactiveClientRegistrationRepository,
    oAuth2AuthorizedClientService: ReactiveOAuth2AuthorizedClientService,
  ): ReactiveOAuth2AuthorizedClientManager {
    val authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build()
    val authorizedClientManager =
      AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
        clientRegistrationRepository,
        oAuth2AuthorizedClientService,
      )
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider)
    return authorizedClientManager
  }
}
