@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository

internal class PrisonerSynchroniserServiceTest {
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val prisonerDifferenceService = mock<PrisonerDifferenceService>()
  private val service = PrisonerSynchroniserService(
    prisonerRepository,
    telemetryClient,
    restrictedPatientService,
    incentivesService,
    prisonerDifferenceService,
  )

  @Nested
  inner class reindex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    internal fun `will save prisoner to repository`() {
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will not save prisoner if no changes`() {
      val existingPrisoner = Prisoner()
      whenever(prisonerRepository.get(any(), any())).thenReturn(existingPrisoner)
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(false)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerDifferenceService, never()).handleDifferences(any(), any(), any(), any())
      verify(prisonerRepository, never()).save(any(), any())
      verify(telemetryClient).trackEvent(
        eq(PRISONER_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "12345"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    internal fun `will call prisoner difference to handle differences`() {
      val existingPrisoner = Prisoner()
      whenever(prisonerRepository.get(any(), any())).thenReturn(existingPrisoner)
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerDifferenceService).handleDifferences(
        eq(existingPrisoner),
        eq(booking),
        check {
          assertThat(it.prisonerNumber).isEqualTo(booking.offenderNo)
        },
        eq("event"),
      )
    }

    @Test
    internal fun `will not call restricted patients if prisoner in a prison`() {
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.reindex(prisonBooking, listOf(GREEN), "event")

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.reindex(noLivingUnitBooking, listOf(GREEN), "event")

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will call restricted patients if prisoner is outside`() {
      val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )

      service.reindex(outsidePrisoner, listOf(GREEN), "event")

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `will throw exception if restricted patients service call fails, but still save prisoner`() {
      val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)

      assertThatThrownBy { service.reindex(outsidePrisoner, listOf(GREEN), "event") }.hasMessage("not today thank you")

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will not call incentives if no booking id`() {
      val noBookingId = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.reindex(noBookingId, listOf(GREEN), "event")

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.reindex(bookingIdBooking, listOf(GREEN), "event")

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will throw exception if incentives service call fails, but still save prisoner`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)

      assertThatThrownBy { service.reindex(booking, listOf(GREEN), "event") }.hasMessage("not today thank you")

      verify(prisonerRepository).save(isA(), isA())
    }
  }

  @Nested
  inner class index {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    internal fun `will save prisoner to repository`() {
      service.index(booking, GREEN)

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      service.index(booking, GREEN)

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will not send telemetry event for insert`() {
      service.index(booking, GREEN)

      verifyNoInteractions(telemetryClient)
    }

    @Test
    internal fun `will not call restricted patients if prisoner in a prison`() {
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.index(prisonBooking, GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.index(noLivingUnitBooking, GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will call restricted patients if prisoner is outside`() {
      val outsideBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )

      service.index(outsideBooking, GREEN)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `will throw exception if restricted patients service call fails`() {
      val outsideBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index(outsideBooking, GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }

    @Test
    internal fun `will not call incentives if no booking id`() {
      val noBookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.index(noBookingIdBooking, GREEN)

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.index(bookingIdBooking, GREEN)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will throw exception if incentives service call fails`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index(booking, GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }
  }

  @Nested
  inner class compareAndMaybeIndex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @BeforeEach
    internal fun setUp() {
      whenever(prisonerRepository.save(any(), any())).thenAnswer { it.arguments[0] }
    }

    @Test
    internal fun `will get incentive level if booking present`() {
      service.compareAndMaybeIndex(booking, listOf(GREEN))

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will not get incentive if there is no booking`() {
      service.compareAndMaybeIndex(OffenderBookingBuilder().anOffenderBooking(null), listOf(GREEN))

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will not call restricted patients if prisoner in a prison`() {
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.compareAndMaybeIndex(prisonBooking, listOf(GREEN))

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.compareAndMaybeIndex(noLivingUnitBooking, listOf(GREEN))

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will call restricted patients if prisoner is outside`() {
      val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )

      service.compareAndMaybeIndex(outsidePrisoner, listOf(GREEN))

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `will do nothing if no differences found`() {
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(false)
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, listOf(GREEN))

      verify(prisonerRepository, never()).save(any(), any())
      verify(prisonerDifferenceService).prisonerHasChanged(any(), any())
      verifyNoMoreInteractions(prisonerDifferenceService)
    }

    @Test
    internal fun `will report differences if found`() {
      whenever(prisonerDifferenceService.prisonerHasChanged(any(), any())).thenReturn(true)
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, listOf(GREEN))

      verify(prisonerDifferenceService).reportDiffTelemetry(any(), any())
      verify(prisonerRepository).save(any(), eq(GREEN))
      verify(prisonerDifferenceService).handleDifferences(any(), any(), any(), any())
    }
  }

  @Nested
  inner class delete {
    @Test
    internal fun `will raise a telemetry event`() {
      service.delete("ABC123D")

      verify(telemetryClient).trackEvent(TelemetryEvents.PRISONER_REMOVED.name, mapOf("prisonerNumber" to "ABC123D"), null)
    }

    @Test
    internal fun `will call prisoner repository to delete the prisoner`() {
      service.delete("ABC123D")

      verify(prisonerRepository).delete("ABC123D")
    }
  }
}
