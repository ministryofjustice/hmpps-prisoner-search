package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class IncentivesService(
  private val incentivesWebClient: WebClient,
) {
  fun getCurrentIncentive(bookingId: Long): IncentiveLevel? =
    incentivesWebClient.get().uri("/incentive-reviews/booking/{bookingId}?with-details=false", bookingId)
      .retrieve()
      .bodyToMono(IncentiveLevel::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
}

data class IncentiveLevel(
  val iepCode: String,
  val iepLevel: String,
  val iepTime: LocalDateTime,
  val nextReviewDate: LocalDate?,
)
