package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class PrisonApiService(val prisonApiWebClient: WebClient) {

  fun getAllAlerts(): List<AlertType> =
    getAlertTypes().map { type ->
      AlertType(
        type = type.code,
        description = type.description,
        active = type.activeFlag == "Y",
        alertCodes = type.subCodes.map {
          AlertCode(it.code, it.description, it.activeFlag == "Y")
        },
      )
    }

  private fun getAlertTypes() = prisonApiWebClient.get()
    .uri("/api/reference-domains/alertTypes")
    .header("Page-Limit", "1000")
    .retrieve()
    .bodyToMono<List<ReferenceCode>>()
    .block()!!
}

private data class ReferenceCode(
  val domain: String,
  val code: String,
  val description: String,
  val activeFlag: String,
  val parentCode: String?,
  val subCodes: List<ReferenceCode>,
)

data class AlertType(
  val type: String,
  val description: String,
  val active: Boolean,
  val alertCodes: List<AlertCode>,
)

data class AlertCode(
  val code: String,
  val description: String,
  val active: Boolean,
)
