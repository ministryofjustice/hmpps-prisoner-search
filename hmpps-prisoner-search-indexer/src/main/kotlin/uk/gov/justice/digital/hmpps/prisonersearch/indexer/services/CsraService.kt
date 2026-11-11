package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentCsra
import java.time.LocalDate

/**
 * Whether the CSRA is taken from the CSRA service (cell-sharing-risk-assessment-api) rather than from Prison API.
 * Set per environment with csra.from-csra-api (CSRA_FROM_CSRA_API). A component, rather than a value read in
 * each class, so that one place decides and tests can switch it.
 */
@Component
class CsraSource(@Value("\${csra.from-csra-api:false}") private val fromCsraApi: Boolean) {
  fun fromCsraApi(): Boolean = fromCsraApi
}

@Service
class CsraService(@Qualifier("csraWebClient") private val csraWebClient: WebClient) {
  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getCurrentCsra(prisonerNumber: String): CsraRatingSummary? = csraWebClient.get()
    .uri("/csra-review/prisoner/{prisonerNumber}/current-rating-summary", prisonerNumber)
    .retrieve()
    .bodyToMono<CsraRatingSummary>()
    .block()
}

/** The CSRA service's current-rating-summary response (only the fields used here). */
data class CsraRatingSummary(
  val prisonerNumber: String,
  val status: String,
  val rating: String? = null,
  val ratingStage: String? = null,
  val ratingDate: LocalDate? = null,
  val ratingDescription: String,
  val ratingShortDescription: String,
  val reviewDueDate: LocalDate? = null,
) {
  fun toCurrentCsra() = CurrentCsra(
    rating = rating,
    ratingStage = ratingStage,
    status = status,
    description = ratingDescription,
    shortDescription = ratingShortDescription,
    ratingDate = ratingDate,
    reviewDueDate = reviewDueDate,
  )
}
