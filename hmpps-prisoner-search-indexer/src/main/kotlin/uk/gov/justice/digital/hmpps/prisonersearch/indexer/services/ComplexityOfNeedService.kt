package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.complexityofneed.model.ComplexityOfNeed
import kotlin.jvm.java

@Service
class ComplexityOfNeedService(
  private val complexityOfNeedWebClient: WebClient,
) {
  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getComplexityOfNeedForPrisoner(offenderNo: String): ComplexityOfNeed? = complexityOfNeedWebClient
    .get()
    .uri("/v1/complexity-of-need/offender-no/{offenderNo}", offenderNo)
    .retrieve()
    .bodyToMono(ComplexityOfNeed::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .block()

// Also consider POST /v1/complexity-of-need/multiple/offender-no
//  [
//  "A0000AA",
//  "B0000BB",
//  "C0000CC"
//  ]
// returning an array of ComplexityOfNeeds.
// possibly to use when building index?
}
