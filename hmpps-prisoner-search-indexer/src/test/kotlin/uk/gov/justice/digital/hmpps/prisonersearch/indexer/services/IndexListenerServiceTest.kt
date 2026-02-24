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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangeAdditionalInformation
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IncentiveChangedMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientAdditionalInformation
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.RestrictedPatientMessage
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.restrictedpatients.model.RestrictedPatientDto
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import java.time.LocalDateTime

internal class IndexListenerServiceTest {

  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val nomisService = mock<NomisService>()
  private val prisonerLocationService = mock<PrisonerLocationService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val hmppsDomainEventEmitter = mock<HmppsDomainEventEmitter>()
  private val indexListenerService =
    IndexListenerService(
      prisonerSynchroniserService,
      nomisService,
      prisonerLocationService,
      telemetryClient,
      hmppsDomainEventEmitter,
    )

  @Nested
  inner class incentiveChange {
    @Test
    fun `will reindex on incentive change`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A1234AA", id = 12345),
          eventType = "some.iep.update",
          description = "some desc",
        ),
        "some.iep.update",
      )

      verify(prisonerSynchroniserService).reindexIncentive(
        check {
          assertThat(it).isEqualTo("A1234AA")
        },
        eq("some.iep.update"),
      )
    }

    @Test
    fun `will not do an overall reindex if prisoner not found`() {
      indexListenerService.incentiveChange(
        IncentiveChangedMessage(
          additionalInformation = IncentiveChangeAdditionalInformation(nomsNumber = "A7089FD", id = 12345),
          eventType = "some.iep.update",
          description = "some desc",
        ),
        "some.iep.update",
      )
    }
  }

  @Nested
  inner class RestrictedPatientChange {
    @Test
    fun `will reindex on restricted patient change`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      val prisonerNumber = booking.offenderNo
      val restrictedPatientDto = RestrictedPatientDto(prisonerNumber, LocalDateTime.now())

      whenever(nomisService.getOffender(prisonerNumber)).thenReturn(booking)
      whenever(prisonerSynchroniserService.getRestrictedPatient(booking)).thenReturn(restrictedPatientDto)

      indexListenerService.restrictedPatientChange(
        RestrictedPatientMessage(
          additionalInformation = RestrictedPatientAdditionalInformation(prisonerNumber),
          eventType = "some.rp.update",
          description = "some desc",
        ),
        "some.rp.update",
      )

      verify(prisonerSynchroniserService).reindexRestrictedPatient(
        prisonerNumber,
        booking,
        restrictedPatientDto,
        "some.rp.update",
      )
      verify(prisonerSynchroniserService).reindexComplexityOfNeedWithGet(
        booking,
        restrictedPatientDto,
        "some.rp.update",
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      indexListenerService.restrictedPatientChange(
        RestrictedPatientMessage(
          additionalInformation = RestrictedPatientAdditionalInformation(prisonerNumber = "A7089FD"),
          eventType = "some.rp.update",
          description = "some desc",
        ),
        "some.rp.update",
      )

      verifyNoInteractions(prisonerSynchroniserService)
    }
  }

  @Nested
  inner class externalMovement {
    @Test
    fun `will reindex on external movement`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      indexListenerService.externalMovement(anExternalMovement(), "EXTERNAL_MOVEMENT")

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("EXTERNAL_MOVEMENT"),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn(null)
      indexListenerService.externalMovement(anExternalMovement(), "EXTERNAL_MOVEMENT")

      verifyNoInteractions(prisonerSynchroniserService)
    }

    private fun anExternalMovement() = ExternalPrisonerMovementMessage(bookingId = 1234)
  }

  @Nested
  inner class offenderBookingChange {
    @Test
    fun `will reindex on offender booking change`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.offenderBookingChange(anOffenderBookingChange(), "BOOKING_CHANGE")

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("BOOKING_CHANGE"),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      indexListenerService.offenderBookingChange(anOffenderBookingChange(), "BOOKING_CHANGE")

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
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      whenever(nomisService.getMergedIdentifiersByBookingId(any())).thenReturn(null)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.offenderBookNumberChange(anOffenderBookingChange(), "BOOKING_CHANGE")

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("BOOKING_CHANGE"),
      )
    }

    @Test
    fun `will delete all merged identifiers`() {
      whenever(nomisService.getMergedIdentifiersByBookingId(any())).thenReturn(
        listOf(
          BookingIdentifier("type", "MERGE1"),
          BookingIdentifier("type", "MERGE2"),
        ),
      )
      indexListenerService.offenderBookNumberChange(anOffenderBookingChange(), "OFFENDER_CHANGE")

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
      indexListenerService.offenderChange(anOffenderChanged(null), "OFFENDER_CHANGED")

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER_CHANGED", "offenderId" to "1234"),
        null,
      )
      verifyNoInteractions(nomisService)
    }

    @Test
    fun `will reindex on offender change`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.offenderChange(anOffenderChanged("A1234BC"), "OFFENDER_CHANGE")

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("OFFENDER_CHANGE"),
      )
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
      indexListenerService.maybeDeleteOffender(anOffenderChanged(null), "OFFENDER-DELETED")

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER-DELETED", "offenderId" to "1234"),
        null,
      )
      verifyNoInteractions(nomisService)
    }

    @Test
    fun `will reindex on OFFENDER-DELETED event if alias deletion`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"), "OFFENDER-DELETED")

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("OFFENDER-DELETED"),
      )
    }

    @Test
    fun `will delete on OFFENDER-DELETED event if no longer exists`() {
      whenever(nomisService.getOffender(any<String>())).thenReturn(null)
      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"), "OFFENDER-DELETED")

      verify(prisonerSynchroniserService).delete("A123BC")
    }

    @Test
    fun `will raise a deletion event on OFFENDER-DELETED event if no longer exists`() {
      whenever(nomisService.getOffender("A123BC")).thenReturn(null)
      whenever(prisonerSynchroniserService.delete("A123BC")).thenReturn(true)

      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"), "OFFENDER-DELETED")

      verify(hmppsDomainEventEmitter).emitPrisonerRemovedEvent("A123BC")
    }

    @Test
    fun `will not raise a deletion event if prisoner never existed`() {
      whenever(nomisService.getOffender("A123BC")).thenReturn(null)
      whenever(prisonerSynchroniserService.delete("A123BC")).thenReturn(false)

      indexListenerService.maybeDeleteOffender(anOffenderChanged("A123BC"), "OFFENDER-DELETED")

      verifyNoInteractions(hmppsDomainEventEmitter)
    }

    private fun anOffenderChanged(prisonerNumber: String?) = OffenderChangedMessage(
      eventType = "OFFENDER-DELETED",
      offenderIdDisplay = prisonerNumber,
      offenderId = 1234,
    )
  }

  @Nested
  inner class BookingDeleted {
    @Test
    fun `will reindex if offender exists`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      val offenderNo = booking.offenderNo
      whenever(nomisService.getOffender(offenderNo)).thenReturn(booking)

      indexListenerService.bookingDeleted(BookingDeletedMessage(offenderNo, booking.bookingId!!), "BOOKING-DELETED")

      verify(prisonerSynchroniserService).reindexUpdate(booking, "BOOKING-DELETED")
    }

    @Test
    fun `will delete if offender does not exist`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      val offenderNo = booking.offenderNo
      whenever(nomisService.getOffender(offenderNo)).thenReturn(null)

      indexListenerService.bookingDeleted(BookingDeletedMessage(offenderNo, booking.bookingId!!), "BOOKING-DELETED")

      verify(prisonerSynchroniserService).delete(offenderNo)
    }
  }

  @Nested
  inner class offenderBookingReassignment {
    @Test
    fun `will create an event for missing offender id display`() {
      indexListenerService.offenderBookingReassigned(
        anOffenderBookingReassignment(
          prisonerNumber = null,
          previousPrisonerNumber = "A1234BC",
        ),
        "OFFENDER_BOOKING-REASSIGNED",
      )

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER_BOOKING-REASSIGNED", "offenderId" to "1234"),
        null,
      )
      verify(nomisService).getOffender("A1234BC")
      verifyNoMoreInteractions(nomisService)
    }

    @Test
    fun `will create an event for missing previous offender id display`() {
      indexListenerService.offenderBookingReassigned(
        anOffenderBookingReassignment(
          prisonerNumber = "A1234BC",
          previousPrisonerNumber = null,
        ),
        "OFFENDER_BOOKING-REASSIGNED",
      )

      verify(telemetryClient).trackEvent(
        "MISSING_OFFENDER_ID_DISPLAY",
        mapOf("eventType" to "OFFENDER_BOOKING-REASSIGNED", "offenderId" to "1234"),
        null,
      )
      verify(nomisService).getOffender("A1234BC")
      verifyNoMoreInteractions(nomisService)
    }

    @Test
    fun `will reindex on offender booking reassignment`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getNomsNumberForBooking(any())).thenReturn("A124BC")
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.offenderBookingReassigned(
        anOffenderBookingReassignment(
          prisonerNumber = "A1234BC",
          previousPrisonerNumber = "A1234BC",
        ),
        "OFFENDER_BOOKING-REASSIGNED",
      )

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("OFFENDER_BOOKING-REASSIGNED"),
      )
      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("OFFENDER_BOOKING-REASSIGNED"),
      )
      // first booking only sent
      verifyNoMoreInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `will reindex on offender booking reassignment with different previous prison number`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      val previousBooking = OffenderBookingBuilder().anOffenderBooking(offenderNo = "A2345CD")
      whenever(nomisService.getOffender("A1234BC")).thenReturn(booking)
      whenever(nomisService.getOffender("A2345CD")).thenReturn(previousBooking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      indexListenerService.offenderBookingReassigned(
        anOffenderBookingReassignment(
          prisonerNumber = "A1234BC",
          previousPrisonerNumber = "A2345CD",
        ),
        "OFFENDER_BOOKING-REASSIGNED",
      )

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("OFFENDER_BOOKING-REASSIGNED"),
      )

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(previousBooking.offenderNo)
        },
        eq("OFFENDER_BOOKING-REASSIGNED"),
      )
    }

    private fun anOffenderBookingReassignment(prisonerNumber: String?, previousPrisonerNumber: String?) = OffenderBookingReassignedMessage(
      offenderId = 1234L,
      previousOffenderId = 2345L,
      bookingId = 12345L,
      offenderIdDisplay = prisonerNumber,
      previousOffenderIdDisplay = previousPrisonerNumber,
    )
  }

  @Nested
  inner class prisonerLocationChange {
    @Test
    fun `will reindex on prisoner location change`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)
      doReturn(Prisoner()).whenever(prisonerSynchroniserService).reindexUpdate(any(), any())
      whenever(prisonerLocationService.findPrisoners(any(), any())).thenReturn(listOf("A124BC"))

      indexListenerService.prisonerLocationChange(
        anPrisonerLocationChange(),
        "AGENCY_INTERNAL_LOCATIONS-UPDATED",
      )

      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("AGENCY_INTERNAL_LOCATIONS-UPDATED"),
      )
      verify(prisonerSynchroniserService).reindexUpdate(
        check {
          assertThat(it.offenderNo).isEqualTo(booking.offenderNo)
        },
        eq("AGENCY_INTERNAL_LOCATIONS-UPDATED"),
      )
      verifyNoMoreInteractions(prisonerSynchroniserService)
    }

    @Test
    fun `will do nothing if new location (missing old description)`() {
      indexListenerService.prisonerLocationChange(
        anPrisonerLocationChange(oldDescription = null),
        "AGENCY_INTERNAL_LOCATIONS-UPDATED",
      )
      verifyNoInteractions(prisonerLocationService)
      verifyNoInteractions(prisonerSynchroniserService)
    }

    private fun anPrisonerLocationChange(prisonId: String = "EWI", oldDescription: String? = "EWI-RES1-2-14") = PrisonerLocationChangedMessage(
      prisonId = prisonId,
      oldDescription = oldDescription,
    )
  }
}
