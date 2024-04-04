package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import kotlinx.coroutines.async
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class PrisonApiService(val prisonApiWebClient: WebClient) {

  fun getAllAlerts(): List<AlertType> {
    return runBlocking {
      val alertTypes = async { getDomainReferenceCodes("ALERT") }
      val alertCodes = async { getDomainReferenceCodes("ALERT_CODE") }

      alertTypes.await().map { alertType ->
        AlertType(
          type = alertType.code,
          description = alertType.description,
          active = alertType.activeFlag == "Y",
          alertCodes = alertCodes.await()
            .filter { it.parentCode == alertType.code }
            .map { AlertCode(it.code, it.description, it.activeFlag == "Y") },
        )
      }
    }
  }

  private suspend fun getDomainReferenceCodes(domain: String) = prisonApiWebClient.get()
    .uri("/api/reference-domains/domains/{domain}/codes", domain)
    .retrieve()
    .bodyToMono<List<ReferenceCode>>()
    .awaitSingle()
}

private data class ReferenceCode(
  val domain: String,
  val code: String,
  val description: String,
  val activeFlag: String,
  val parentCode: String?,
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
