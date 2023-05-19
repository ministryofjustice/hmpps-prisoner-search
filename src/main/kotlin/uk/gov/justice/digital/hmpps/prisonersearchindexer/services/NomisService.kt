package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.OffenderBooking
import java.time.Duration

@Service
class NomisService(
  val prisonApiWebClient: WebClient,
  @Value("\${api.offender.timeout:20s}") val offenderTimeout: Duration,
) {

  private val identifiers = object : ParameterizedTypeReference<List<BookingIdentifier>>() {}

  fun getOffendersIds(offset: Long = 0, size: Int = 10): OffenderResponse {
    return prisonApiWebClient.get()
      .uri("/api/offenders/ids")
      .header("Page-Offset", offset.toString())
      .header("Page-Limit", size.toString())
      .retrieve()
      .toEntityList(OffenderId::class.java)
      .timeout(Duration.ofMinutes(10))
      .map {
        OffenderResponse(
          it.body,
          it.headers.get("Total-Records")?.first()?.toLongOrNull() ?: 0,
        )
      }.block() ?: OffenderResponse()
  }

  fun getNomsNumberForBooking(bookingId: Long): String? {
    return prisonApiWebClient.get()
      .uri("/api/bookings/$bookingId?basicInfo=true")
      .retrieve()
      .bodyToMono(OffenderBooking::class.java)
      .onErrorResume(NotFound::class.java) { Mono.empty() }
      .block(offenderTimeout)
      ?.offenderNo
  }

  fun getOffender(offenderNo: String): OffenderBooking? = prisonApiWebClient.get()
    .uri("/api/offenders/$offenderNo")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block(offenderTimeout)

  fun getMergedIdentifiersByBookingId(bookingId: Long): List<BookingIdentifier>? = prisonApiWebClient.get()
    .uri("/api/bookings/$bookingId/identifiers?type=MERGED")
    .retrieve()
    .bodyToMono(identifiers)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block(offenderTimeout)
}

data class OffenderId(
  val offenderNumber: String,
)

data class OffenderResponse(
  val offenderIds: List<OffenderId>? = emptyList(),
  val totalRows: Long = 0,
)

data class BookingIdentifier(
  val type: String,
  val value: String,
)
