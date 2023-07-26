@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
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
  private val telemetryClient = mock<TelemetryClient>()
  private val indexListenerService =
    IndexListenerService(indexStatusService, prisonerSynchroniserService, nomisService, telemetryClient)

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
        IndexStatus(
          currentIndex = GREEN,
          currentIndexState = IndexState.CANCELLED,
          otherIndexState = IndexState.ABSENT,
        ),
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

  @Nested
  inner class externalMovement {
    @Test
    fun `will reindex on external movement`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.externalMovement(anExternalMovement())

      verify(prisonerSynchroniserService).reindex(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq(listOf(GREEN)),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn(null)
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      indexListenerService.externalMovement(anExternalMovement())

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `will do nothing if no active indices`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(
          currentIndex = GREEN,
          currentIndexState = IndexState.CANCELLED,
          otherIndexState = IndexState.ABSENT,
        ),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      indexListenerService.externalMovement(anExternalMovement())

      verifyNoInteractions(prisonerSynchroniserService)
    }

    private fun anExternalMovement() = ExternalPrisonerMovementMessage(
      bookingId = 1234,
      movementSeq = 1,
      offenderIdDisplay = "A1234BC",
      fromAgencyLocationId = "MDI",
      toAgencyLocationId = "DNI",
      directionCode = "IN",
      movementType = "TFR",
    )
  }

  @Nested
  inner class offenderBookingChange {
    @Test
    fun `will reindex on offender booking change`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.offenderBookingChange(anOffenderBookingChange())

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
      indexListenerService.offenderBookingChange(anOffenderBookingChange())

      verifyNoInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `will do nothing if no active indices`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(
          currentIndex = GREEN,
          currentIndexState = IndexState.CANCELLED,
          otherIndexState = IndexState.ABSENT,
        ),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      indexListenerService.offenderBookingChange(anOffenderBookingChange())

      verifyNoInteractions(prisonerSynchroniserService)
    }

    private fun anOffenderBookingChange() = OffenderBookingChangedMessage(
      bookingId = 1234,
    )
  }

  @Nested
  inner class offenderBookNumberChange {
    @Test
    fun `will reindex on book number change`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      whenever(nomisService.getMergedIdentifiersByBookingId(any())).thenReturn(null)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.offenderBookNumberChange(anOffenderBookingChange())

      verify(prisonerSynchroniserService).reindex(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq(listOf(GREEN)),
      )
    }

    @Test
    fun `will delete all merged identifiers`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      whenever(nomisService.getMergedIdentifiersByBookingId(any())).thenReturn(
        listOf(
          BookingIdentifier("type", "MERGE1"),
          BookingIdentifier("type", "MERGE2"),
        ),
      )
      indexListenerService.offenderBookNumberChange(anOffenderBookingChange())

      verify(prisonerSynchroniserService).delete("MERGE1")
      verify(prisonerSynchroniserService).delete("MERGE2")
    }

    private fun anOffenderBookingChange() = OffenderBookingChangedMessage(
      bookingId = 1234,
    )
  }

  @Nested
  inner class offenderChange {
    @Test
    fun `will create an event for missing offender id display`() {
      indexListenerService.offenderChange(anOffenderChanged(null))

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER_CHANGED", "offenderId" to "1234"),
        null,
      )
      verifyNoInteractions(nomisService)
    }

    @Test
    fun `will reindex on offender change`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.offenderChange(anOffenderChanged("A1234BC"))

      verify(prisonerSynchroniserService).reindex(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq(listOf(GREEN)),
      )
    }

    private fun anOffenderChanged(prisonerNumber: String?) = OffenderChangedMessage(
      offenderId = 1234,
      eventType = "OFFENDER_CHANGED",
      offenderIdDisplay = prisonerNumber,
    )
  }

  @Nested
  inner class deleteOffender {
    @Test
    fun `will create an event for missing offender id display`() {
      indexListenerService.deleteOffender(anOffenderChanged(null))

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER_CHANGED", "offenderId" to "1234"),
        null,
      )
      verifyNoInteractions(nomisService)
    }

    @Test
    fun `will delete the prisoner on data compliance delete offender`() {
      indexListenerService.deleteOffender(anOffenderChanged("A1234BC"))

      verify(prisonerSynchroniserService).delete("A1234BC")
    }

    private fun anOffenderChanged(prisonerNumber: String?) = OffenderChangedMessage(
      offenderId = 1234,
      eventType = "OFFENDER_CHANGED",
      offenderIdDisplay = prisonerNumber,
    )
  }

  @Nested
  inner class maybeDeleteOffender {
    @Test
    fun `will create an event for missing offender id display`() {
      indexListenerService.maybeDeleteOffender(anOffenderChanged(null))

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER-DELETED", "offenderId" to "1234"),
        null,
      )
      verifyNoInteractions(nomisService)
    }

    @Test
    fun `will reindex on OFFENDER-DELETED event if alias deletion`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndex = GREEN, currentIndexState = IndexState.COMPLETED),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindex(any(), any())
      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"))

      verify(prisonerSynchroniserService).reindex(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq(listOf(GREEN)),
      )
    }

    @Test
    fun `will delete on OFFENDER-DELETED event if no longer exists`() {
      whenever(nomisService.getOffender(any())).thenReturn(null)
      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"))

      verify(prisonerSynchroniserService).delete("A123BC")
    }

    @Test
    fun `will do nothing if no active indices`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(
          currentIndex = GREEN,
          currentIndexState = IndexState.CANCELLED,
          otherIndexState = IndexState.ABSENT,
        ),
      )
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)
      indexListenerService.maybeDeleteOffender(anOffenderChanged("A1234BC"))

      verifyNoInteractions(prisonerSynchroniserService)
    }

    private fun anOffenderChanged(prisonerNumber: String?) = OffenderChangedMessage(
      eventType = "OFFENDER-DELETED",
      offenderIdDisplay = prisonerNumber,
      offenderId = 1234,
    )
  }
}
