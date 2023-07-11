package uk.gov.justice.digital.hmpps.prisonersearch.search.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

abstract class HealthCheck(private val webClient: WebClient) : HealthIndicator {

  override fun health(): Health = webClient.get()
    .uri("/health/ping")
    .retrieve()
    .toEntity(String::class.java)
    .flatMap { Mono.just(Health.up().withDetail("HttpStatus", it?.statusCode).build()) }
    .onErrorResume(WebClientResponseException::class.java) {
      Mono.just(
        Health.down(it).withDetail("body", it.responseBodyAsString).withDetail("HttpStatus", it.statusCode).build(),
      )
    }
    .onErrorResume(Exception::class.java) { Mono.just(Health.down(it).build()) }
    .block() ?: Health.down().withDetail("HttpStatus", "No response returned from ping").build()
}

@Component("hmppsAuth")
class AuthHealth(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthCheck(webClient)
