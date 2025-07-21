package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.RestrictedPatient
import java.time.LocalDateTime

@Service
class RestrictedPatientService(@Qualifier("restrictedPatientsWebClient") private val webClient: WebClient) {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatient? = webClient.get()
    .uri("/restricted-patient/prison-number/{prisonerNumber}", prisonerNumber)
    .retrieve()
    .bodyToMono(RestrictedPatientDto::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .block()
    ?.let {
      RestrictedPatient(
        supportingPrisonId = it.supportingPrison.agencyId,
        dischargedHospital = it.hospitalLocation,
        dischargeDate = it.dischargeTime.toLocalDate(),
        dischargeDetails = it.commentText,
      )
    }
}

private data class RestrictedPatientDto(
  val id: Long,
  val prisonerNumber: String,
  val supportingPrison: Agency,
  val hospitalLocation: Agency,
  val dischargeTime: LocalDateTime,
  val commentText: String? = null,
)
