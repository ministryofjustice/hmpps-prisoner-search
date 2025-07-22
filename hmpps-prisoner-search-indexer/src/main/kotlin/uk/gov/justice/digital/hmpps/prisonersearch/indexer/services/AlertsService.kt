package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.annotation.JsonCreator
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
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.alerts.model.Alert

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
        .queryParam("sort", "activeFrom,DESC")
        .queryParam("sort", "createdAt,ASC")
        .build(offenderNo)
    }
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<RestResponsePage<Alert>>() {})
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .block()
    ?.let { result ->
      // since a person can not typically have multiple active alerts the page size needs to be as big as the number of different alert codes (213)
      // so give result a reasonable headroom for growth and in the unlikely event of exceeding the size than throw an error and we can increase size with a code change
      val totalElements = result.totalElements
      val numberOfElements = result.numberOfElements
      if (totalElements != numberOfElements.toLong()) throw IllegalStateException("Page size of 1000 for /prisoners/{offenderNo}/alerts not big enough $totalElements not equal to $numberOfElements")
      // Ensure order is fixed. This is important as any change will result in the refresh firing a lot of diff events
      result.content.sortedWith(
        compareByDescending<Alert> { it.activeFrom }
          .thenBy { it.createdAt }, // Note this is Ascending
      )
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
