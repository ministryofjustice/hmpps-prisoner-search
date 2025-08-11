package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class AlertsApiService(val alertsApiWebClient: WebClient) {

  fun getAllAlerts(): List<PrisonAlertType> = getAlertTypes().map { type ->
    PrisonAlertType(
      type = type.code,
      description = type.description,
      active = type.isActive,
      alertCodes = type.alertCodes?.map {
        PrisonAlertCode(it.code, it.description, it.isActive)
      } ?: emptyList(),
    )
  }

  private fun getAlertTypes() = alertsApiWebClient.get()
    .uri("/alert-types?includeInactive=true")
    .retrieve()
    .bodyToMono<List<ReferenceCode>>()
    .block()!!
}

private data class ReferenceCode(
  val code: String,
  val description: String,
  val isActive: Boolean,
  val alertCodes: List<ReferenceCode>?,
)

data class PrisonAlertType(
  val type: String,
  val description: String,
  val active: Boolean,
  val alertCodes: List<PrisonAlertCode>,
)

data class PrisonAlertCode(
  val code: String,
  val description: String,
  val active: Boolean,
)
