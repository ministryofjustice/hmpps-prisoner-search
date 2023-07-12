@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.AssignedLivingUnit

internal class PrisonerSynchroniserServiceTest {
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val service = PrisonerSynchroniserService(
    prisonerRepository,
    telemetryClient,
    restrictedPatientService,
    incentivesService,
  )

  @Nested
  inner class reindex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    internal fun `will save prisoner to repository`() {
      service.reindex(booking, listOf(GREEN))

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      service.reindex(booking, listOf(GREEN))

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will send telemetry event for update`() {
      service.reindex(booking, listOf(GREEN))

      verify(telemetryClient).trackEvent(eq("PRISONER_UPDATED"), any(), isNull())
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

      service.reindex(prisonBooking, listOf(GREEN))

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.reindex(noLivingUnitBooking, listOf(GREEN))

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

      service.reindex(outsidePrisoner, listOf(GREEN))

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

      assertThatThrownBy { service.reindex(outsidePrisoner, listOf(GREEN)) }.hasMessage("not today thank you")

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will not call incentives if no booking id`() {
      val noBookingId = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.reindex(noBookingId, listOf(GREEN))

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.reindex(bookingIdBooking, listOf(GREEN))

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will throw exception if incentives service call fails, but still save prisoner`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.reindex(booking, listOf(GREEN)) }.hasMessage("not today thank you")

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
}
