package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
@EnableAsync
@EnableRetry
class WebClientConfiguration(
  @Value("\${api.base.url.prison-api}") val prisonApiBaseUri: String,
  @Value("\${api.base.url.incentives}") val incentivesBaseUri: String,
  @Value("\${api.base.url.restricted-patients}") val restrictedPatientBaseUrl: String,
  @Value("\${api.base.url.alerts}") val alertsBaseUri: String,
  @Value("\${api.base.url.complexity-of-need}") val complexityOfNeedBaseUri: String,
  @Value("\${api.base.url.prison-register}") private val prisonRegisterUri: String,
  @Value("\${api.base.url.oauth}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {
  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiBaseUri, healthTimeout)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "prison-api", url = prisonApiBaseUri, timeout)

  @Bean
  fun restrictedPatientsHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(restrictedPatientBaseUrl, healthTimeout)

  @Bean
  fun restrictedPatientsWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "restricted-patients-api", url = restrictedPatientBaseUrl, timeout)

  @Bean
  fun incentivesHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(incentivesBaseUri, healthTimeout)

  @Bean
  fun incentivesWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "incentives-api", url = incentivesBaseUri, timeout)

  @Bean
  fun alertsHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(alertsBaseUri, healthTimeout)

  @Bean
  fun alertsWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "alerts-api", url = alertsBaseUri, timeout)

  @Bean
  fun complexityOfNeedHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(complexityOfNeedBaseUri, healthTimeout)

  @Bean
  fun complexityOfNeedWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "complexity-of-need-api", url = complexityOfNeedBaseUri, timeout)

  @Bean
  fun prisonRegisterWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonRegisterUri, healthTimeout)
}
