package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.incentives.api.MaintainIncentiveReviewsApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.incentives.model.IncentiveReviewSummary

@Service
class IncentivesService(
  incentivesWebClient: WebClient,
) {
  private val incentivesApi: MaintainIncentiveReviewsApi = MaintainIncentiveReviewsApi(incentivesWebClient)

  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getCurrentIncentive(prisonNumber: String): IncentiveReviewSummary? = incentivesApi.prepare(
    incentivesApi.getPrisonerIncentiveReviewHistoryRequestConfig(prisonerNumber = prisonNumber),
  )
    .retrieve()
    .bodyToMono<IncentiveReviewSummary>()
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .block()
}
