package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.csra.api.CSRAReviewApi
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.csra.model.CsraRatingSummary

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
class CsraService(csraWebClient: WebClient) {
  private val csraApi = CSRAReviewApi(csraWebClient)

  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getCurrentCsra(prisonerNumber: String): CsraRatingSummary? = csraApi.prepare(
    csraApi.getCurrentRatingSummaryRequestConfig(prisonerNumber = prisonerNumber),
  )
    .retrieve()
    .bodyToMono<CsraRatingSummary>()
    .block()
}
