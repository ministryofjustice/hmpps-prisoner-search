package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AlertsService(
  private val alertsWebClient: WebClient,
) {
  @Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
  fun getActiveAlertsForPrisoner(offenderNo: String): List<Alert>? = alertsWebClient
    .get()
    .uri {
      it.path("/prisoners/{offenderNo}/alerts")
        .queryParam("isActive", true)
        .queryParam("page", 0)
        .queryParam("size", 1000)
        .build(offenderNo)
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<RestResponsePage<Alert>>() {})
    .block()
    .let { result ->
      // since a person can not typically have multiple active alerts the page size needs to be as big as the number of different alert codes (213)
      // so give result a reasonable headroom for growth and in the unlikely event of exceeding the size than throw an error and we can increase size with a code change
      val totalElements = result?.totalElements
      val numberOfElements = result.numberOfElements
      if (totalElements != numberOfElements.toLong()) throw IllegalStateException("Page size of 1000 for /prisoners/{offenderNo}/alerts not big enough $totalElements not equal to $numberOfElements")
      result.content
    }
}

class RestResponsePage<T> : PageImpl<T> {
  @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
  constructor(
    @JsonProperty("content") content: List<T>,
    @JsonProperty("number") number: Int,
    @JsonProperty("size") size: Int,
    @JsonProperty("totalElements") totalElements: Long?,
    @JsonProperty("pageable") pageable: JsonNode,
    @JsonProperty("last") last: Boolean,
    @JsonProperty("totalPages") totalPages: Int,
    @JsonProperty("sort") sort: JsonNode,
    @JsonProperty("first") first: Boolean,
    @JsonProperty("numberOfElements") numberOfElements: Int,
  ) : super(content, PageRequest.of(number, size.coerceAtLeast(1)), totalElements!!)

  constructor(content: List<T>, pageable: Pageable, total: Long) : super(content, pageable, total) {}

  constructor(content: List<T>) : super(content) {}

  constructor() : super(ArrayList<T>()) {}
}

data class Alert(
  val alertUuid: UUID,
  val prisonNumber: String,
  val alertCode: AlertCodeSummary,
  val description: String?,
  val authorisedBy: String?,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val activeFrom: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val activeTo: LocalDate?,
  val isActive: Boolean,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val createdAt: LocalDateTime,
  val createdBy: String,
  val createdByDisplayName: String,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val lastModifiedAt: LocalDateTime?,
  val lastModifiedBy: String?,
  val lastModifiedByDisplayName: String?,
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val activeToLastSetAt: LocalDateTime?,
  val activeToLastSetBy: String?,
  val activeToLastSetByDisplayName: String?,
)

data class AlertCodeSummary(
  val alertTypeCode: String,
  val alertTypeDescription: String,
  val code: String,
  val description: String,
)
