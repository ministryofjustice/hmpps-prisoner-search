package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClientRequest
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBookingOld
import java.time.Duration

@Service
class NomisService(
  val prisonApiWebClient: WebClient,
) {
  private fun getOffendersIds(page: Int = 0, size: Int = 10) = prisonApiWebClient.get()
    .uri {
      it.path("/api/prisoners/prisoner-numbers")
        .queryParam("page", page)
        .queryParam("size", size)
        .build()
    }
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(2))
    }
    .retrieve()
    .bodyToMono(PrisonerNumberPage::class.java)
    .block()!!

  fun getTotalNumberOfPrisoners(): Long = getOffendersIds(0, 1).totalElements

  fun getPrisonerNumbers(page: Int, pageSize: Int): List<String> =
    getOffendersIds(page, pageSize).content

  fun getNomsNumberForBooking(bookingId: Long): String? = prisonApiWebClient.get()
    .uri("/api/bookings/{bookingId}?basicInfo=true", bookingId)
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
    ?.offenderNo

  fun getOffender(offenderNo: String): OffenderBooking? = prisonApiWebClient.get()
    .uri("/api/offenders/{offenderNo}", offenderNo)
    .retrieve()
    .bodyToMono(OffenderBookingOld::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
    ?.toOffenderBooking()

  fun getOffenderNewEndpoint(offenderNo: String): OffenderBooking? = prisonApiWebClient.get()
    .uri("/api/prisoner-search/offenders/{offenderNo}", offenderNo)
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()

  fun getMergedIdentifiersByBookingId(bookingId: Long): List<BookingIdentifier>? = prisonApiWebClient.get()
    .uri("/api/bookings/{bookingId}/identifiers?type=MERGED", bookingId)
    .retrieve()
    .bodyToMono(object : ParameterizedTypeReference<List<BookingIdentifier>>() {})
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
}

data class PrisonerNumberPage(
  val content: List<String> = emptyList(),
  val totalElements: Long = 0,
)

data class BookingIdentifier(
  val type: String,
  val value: String,
)
