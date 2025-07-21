package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.restrictedpatients.model.RestrictedPatientDto

@Service
class RestrictedPatientService(@Qualifier("restrictedPatientsWebClient") private val webClient: WebClient) {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? = webClient.get()
    .uri("/restricted-patient/prison-number/{prisonerNumber}", prisonerNumber)
    .retrieve()
    .bodyToMono(RestrictedPatientDto::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .block()
}
