package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.IncentiveLevel

@Service
class IncentivesService(
  private val incentivesWebClient: WebClient,
) {
  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getCurrentIncentive(bookingId: Long): IncentiveLevel? = incentivesWebClient.get().uri("/incentive-reviews/booking/{bookingId}?with-details=false", bookingId)
    .retrieve()
    .bodyToMono(IncentiveLevel::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .block()
}
