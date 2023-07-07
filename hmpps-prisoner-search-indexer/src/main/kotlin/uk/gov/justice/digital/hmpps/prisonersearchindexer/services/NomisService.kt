package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.OffenderBooking
import java.time.Duration

@Service
class NomisService(
  val prisonApiWebClient: WebClient,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private fun getOffendersIds(page: Int = 0, size: Int = 10) = prisonApiWebClient.get()
    .uri {
      it.path("/api/prisoners/prisoner-numbers")
        .queryParam("page", page)
        .queryParam("size", size)
        .build()
    }
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .bodyToMono(PrisonerNumberPage::class.java)
    .block()!!

  fun getTotalNumberOfPrisoners(): Long = getOffendersIds(0, 1).totalRecords

  fun getPrisonerNumbers(page: Int, pageSize: Int): List<String> =
    getOffendersIds(page, pageSize).content

  fun getNomsNumberForBooking(bookingId: Long): String? = prisonApiWebClient.get()
    .uri("/api/bookings/$bookingId?basicInfo=true")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
    ?.offenderNo

  fun getOffender(offenderNo: String): OffenderBooking? = prisonApiWebClient.get()
    .uri("/api/offenders/$offenderNo")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
}

data class PrisonerNumberPage(
  val content: List<String> = emptyList(),
  val totalRecords: Long = 0,
)
