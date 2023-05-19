package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class RestrictedPatientService(@Qualifier("restrictedPatientsWebClient") private val webClient: WebClient) {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? =
    try {
      webClient.get().uri("/restricted-patient/prison-number/$prisonerNumber")
        .retrieve()
        .bodyToMono(RestrictedPatientDto::class.java)
        .block()
    } catch (e: WebClientResponseException) {
      if (e.statusCode != HttpStatus.NOT_FOUND) throw e
      null
    }
}

data class RestrictivePatient(
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
