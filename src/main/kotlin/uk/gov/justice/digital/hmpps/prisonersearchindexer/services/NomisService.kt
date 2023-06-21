package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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

  private fun getOffendersIds(offset: Long = 0, size: Long = 10) = prisonApiWebClient.get()
    .uri("/api/offenders/ids")
    .header("Page-Offset", offset.toString())
    .header("Page-Limit", size.toString())
    .httpRequest {
      it.getNativeRequest<HttpClientRequest>().responseTimeout(Duration.ofMinutes(1))
    }
    .retrieve()
    .toEntityList(OffenderId::class.java)
    .map {
      OffenderResponse(
        it.body,
        it.headers["Total-Records"]?.first()?.toLongOrNull() ?: 0,
      )
    }.block() ?: OffenderResponse()

  fun getTotalNumberOfPrisoners(): Long = getOffendersIds(0, 1).totalRows

  fun getPrisonerNumbers(page: Long, pageSize: Long): List<OffenderId> =
    getOffendersIds(page, pageSize).offenderIds ?: emptyList()

  fun getNomsNumberForBooking(bookingId: Long): String? = prisonApiWebClient.get()
    .uri("/api/bookings/$bookingId?basicInfo=true")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(NotFound::class.java) { Mono.empty() }
    .block()
    ?.offenderNo

  fun getOffender(offenderNo: String): Either<PrisonerError, OffenderBooking> = prisonApiWebClient.get()
    .uri("/api/offenders/$offenderNo")
    .retrieve()
    .bodyToMono(OffenderBooking::class.java)
    .onErrorResume(::emptyIfNotFound)
    .block()?.right() ?: PrisonerNotFoundError(offenderNo).left()
    .also { log.error("Prisoner with offenderNo {} not found", offenderNo) }

  private fun emptyIfNotFound(exception: Throwable): Mono<out OffenderBooking> =
    if (exception is NotFound) Mono.empty() else Mono.error(exception)
}

data class OffenderId(
  val offenderNumber: String,
)

private data class OffenderResponse(
  val offenderIds: List<OffenderId>? = emptyList(),
  val totalRows: Long = 0,
)
