package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.LocalDate
import java.time.LocalDateTime

@Service
interface RestrictedPatientService {
  fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto?
}

@Service
@ConditionalOnProperty(value = ["api.base.url.restricted-patients"])
class RestrictedPatientServiceImpl(@Qualifier("restrictedPatientsWebClient") private val webClient: WebClient) :
  RestrictedPatientService {
  override fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? =
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

@Service
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isBlank('\${api.base.url.restricted-patients:}')")
class StubRestrictedPatientService : RestrictedPatientService {
  override fun getRestrictedPatient(prisonerNumber: String): RestrictedPatientDto? = null
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
