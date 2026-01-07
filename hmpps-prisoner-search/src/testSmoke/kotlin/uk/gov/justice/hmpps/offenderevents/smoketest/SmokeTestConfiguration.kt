package uk.gov.justice.hmpps.offenderevents.smoketest

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.Builder
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient

@ConditionalOnProperty(name = ["smoketest.enabled"], havingValue = "true")
@EnableWebSecurity
@Import(OAuth2ClientAutoConfiguration::class, OAuth2ClientWebSecurityAutoConfiguration::class)
class SmokeTestConfiguration(@Value("\${smoketest.endpoint.url}") private val smokeTestUrl: String) {
  private val webClientBuilder: Builder = WebClient.builder()

  @Bean
  fun smokeTestWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient = webClientBuilder.authorisedWebClient(authorizedClientManager, registrationId = "smoketest-service", url = smokeTestUrl)
}
