@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangeAdditionalInformation
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder

internal class IndexListenerServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val nomisService = mock<NomisService>()
  private val indexListenerService = IndexListenerService(indexStatusService, prisonerSynchroniserService, nomisService)

  @Nested
  inner class incentiveChange {
    @Test
    fun `will reindex on incentive change`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A7089FD", id = 12345),
          eventType = "some.iep.update",
          description = "some desc",
        ),
      )

      verify(prisonerSynchroniserService).reindex(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq(listOf(GREEN)),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      indexListenerService.incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A7089FD", id = 12345),
          eventType = "some.iep.update",
          description = "some desc",
        ),
      )

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `will do nothing if no active indices`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.CANCELLED, otherIndexState = IndexState.ABSENT),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      indexListenerService.incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A7089FD", id = 12345),
          eventType = "some.iep.update",
          description = "some desc",
        ),
      )

      verifyNoInteractions(prisonerSynchroniserService)
    }
  }
}
