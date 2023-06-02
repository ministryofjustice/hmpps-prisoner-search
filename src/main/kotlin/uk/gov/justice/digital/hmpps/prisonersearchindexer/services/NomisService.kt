package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.slf4j.LoggerFactory
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
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private val identifiers = object : ParameterizedTypeReference<List<BookingIdentifier>>() {}

  fun getOffendersIds(offset: Long = 0, size: Long = 10): OffenderResponse {
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
          it.headers["Total-Records"]?.first()?.toLongOrNull() ?: 0,
        )
      }.block() ?: OffenderResponse()
  }

  fun getNomsNumberForBooking(bookingId: Long): String? = prisonApiWebClient.get()
    .uri("/api/bookings/$bookingId?basicInfo=true")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block(offenderTimeout)
    ?.offenderNo

  fun getOffender(offenderNo: String): Either<PrisonerError, OffenderBooking> = prisonApiWebClient.get()
    .uri("/api/offenders/$offenderNo")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(::emptyIfNotFound)
    .block(offenderTimeout)?.right() ?: PrisonerNotFoundError(offenderNo).left()
    .also { log.error("Prisoner with offenderNo {} not found", offenderNo) }

  fun getMergedIdentifiersByBookingId(bookingId: Long): List<BookingIdentifier>? = prisonApiWebClient.get()
    .uri("/api/bookings/$bookingId/identifiers?type=MERGED")
    .retrieve()
    .bodyToMono(identifiers)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block(offenderTimeout)

  private fun emptyIfNotFound(exception: Throwable): Mono<out OffenderBooking> =
    if (exception is NotFound) Mono.empty() else Mono.error(exception)
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
