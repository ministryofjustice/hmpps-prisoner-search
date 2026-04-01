@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.concurrent.CompletableFuture

class MaintainIndexServiceTest {

  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val hmppsQueueService = mock<HmppsQueueService>()
  private val nomisService = mock<NomisService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val maintainIndexService = MaintainIndexService(prisonerSynchroniserService, prisonerRepository, nomisService, telemetryClient)

  private val indexSqsClient = mock<SqsAsyncClient>()
  private val indexSqsDlqClient = mock<SqsAsyncClient>()

  init {
    whenever(hmppsQueueService.findByQueueId("index")).thenReturn(HmppsQueue("index", indexSqsClient, "index-queue", indexSqsDlqClient, "index-dlq"))
    whenever(indexSqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-queue").build()))
    whenever(indexSqsDlqClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("arn:eu-west-1:index-dlq").build()))
  }

  @Nested
  inner class IndexOffender {
    @Test
    fun `will delegate to synchronisation service if prisoner found in NOMIS`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(prisonerSynchroniserService.reindexUpdate(any(), any()))
        .thenReturn(
          Prisoner().apply {
            prisonerNumber = booking.offenderNo
          },
        )
      whenever(nomisService.getOffender(booking.offenderNo)).thenReturn(booking)

      maintainIndexService.indexPrisoner(booking.offenderNo)

      verify(prisonerSynchroniserService).reindexUpdate(booking, "MAINTAIN")
      verify(prisonerSynchroniserService).reindexIncentive(booking.offenderNo, "MAINTAIN")
      verify(prisonerSynchroniserService).reindexRestrictedPatient(booking.offenderNo, booking, null, "MAINTAIN")
    }

    @Test
    fun `will delete from index if prisoner only found in indices`() {
      whenever(prisonerRepository.get(any())).thenReturn(Prisoner())

      maintainIndexService.indexPrisoner("ABC123D")

      verify(prisonerSynchroniserService).delete("ABC123D")
    }

    @Test
    fun `will raise a telemetry event if prisoner not found in NOMIS or indices`() {
      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(telemetryClient).trackEvent(TelemetryEvents.PRISONER_NOT_FOUND.name, mapOf("prisonerNumber" to "ABC123D"), null)
    }

    @Test
    fun `will return the not found if prisoner not found in NOMIS or indices`() {
      assertThatThrownBy { maintainIndexService.indexPrisoner("ABC123D") }
        .isInstanceOf(PrisonerNotFoundException::class.java)
        .hasMessageContaining("The prisoner ABC123D")
    }

    @Test
    fun `Offender is updated but prisoner not found`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)

      assertThatThrownBy { maintainIndexService.indexPrisoner("SOME_CRN") }
        .isInstanceOf(PrisonerNotFoundException::class.java)

      verify(prisonerSynchroniserService).reindexUpdate(booking, "MAINTAIN")
    }
  }
}
