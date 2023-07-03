package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RestrictedPatientService(@Qualifier("restrictedPatientsWebClient") private val webClient: WebClient) {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatient? =
    webClient.get().uri("/restricted-patient/prison-number/$prisonerNumber")
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

data class RestrictedPatient(
  var supportingPrisonId: String?,
  val dischargedHospital: Agency?,
  val dischargeDate: LocalDate,
  val dischargeDetails: String?,
)

data class Agency(
  val agencyId: String,
  val description: String? = null,
  val longDescription: String? = null,
  val agencyType: String,
  val active: Boolean,
)

data class RestrictedPatientDto(
  val id: Long,
  val prisonerNumber: String,
  val supportingPrison: Agency,
  val hospitalLocation: Agency,
  val dischargeTime: LocalDateTime,
  val commentText: String? = null,
)
