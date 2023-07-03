@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.dto.nomis.AssignedLivingUnit

internal class PrisonerSynchroniserServiceTest {
  private val nomisService = mock<NomisService>()
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val service = PrisonerSynchroniserService(
    prisonerRepository,
    telemetryClient,
    nomisService,
    restrictedPatientService,
    incentivesService,
  )

  @Nested
  inner class reindex {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisService.getOffender(any()))
        .thenReturn(
          OffenderBookingBuilder().anOffenderBooking().right(),
        )
    }

    @Test
    fun `should retrieve prisoner`() {
      service.reindex("X12345", GREEN)

      verify(nomisService).getOffender("X12345")
    }

    @Test
    internal fun `will save prisoner to repository`() {
      service.reindex("X12345", GREEN)

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      service.reindex("X12345", GREEN)

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will send telemetry event for update`() {
      service.reindex("X12345", GREEN)

      verify(telemetryClient).trackEvent(eq("PRISONER_UPDATED"), any(), isNull())
    }

    @Test
    internal fun `will not call restricted patients if prisoner in a prison`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "MDI",
            locationId = 1,
            description = "Moorland",
            agencyName = "Moorland",
          ),
        ).right(),
      )

      service.reindex("X12345", GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = null,
        ).right(),
      )

      service.reindex("X12345", GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will call restricted patients if prisoner is outside`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "OUT",
            locationId = 1,
            description = "Outside",
            agencyName = "Outside Prison",
          ),
        ).right(),
      )

      service.reindex("X12345", GREEN)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `will throw exception if restricted patients service call fails, but still save prisoner`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "OUT",
            locationId = 1,
            description = "Outside",
            agencyName = "Outside Prison",
          ),
        ).right(),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.reindex("X12345", GREEN) }.hasMessage("not today thank you")

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will not call incentives if no booking id`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          bookingId = null,
        ).right(),
      )

      service.reindex("X12345", GREEN)

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will call incentives service if there is a booking`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          bookingId = 12345L,
        ).right(),
      )

      service.reindex("X12345", GREEN)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will throw exception if incentives service call fails, but still save prisoner`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.reindex("X12345", GREEN) }.hasMessage("not today thank you")

      verify(prisonerRepository).save(isA(), isA())
    }
  }

  @Nested
  inner class index {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisService.getOffender(any()))
        .thenReturn(
          OffenderBookingBuilder().anOffenderBooking().right(),
        )
    }

    @Test
    fun `should retrieve prisoner`() {
      service.index("X12345", GREEN)

      verify(nomisService).getOffender("X12345")
    }

    @Test
    internal fun `will save prisoner to repository`() {
      service.index("X12345", GREEN)

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      service.index("X12345", GREEN)

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will not send telemetry event for insert`() {
      service.index("X12345", GREEN)

      verifyNoInteractions(telemetryClient)
    }

    @Test
    internal fun `will not call restricted patients if prisoner in a prison`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "MDI",
            locationId = 1,
            description = "Moorland",
            agencyName = "Moorland",
          ),
        ).right(),
      )

      service.index("X12345", GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will not call restricted patients if assigned living unit not set`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = null,
        ).right(),
      )

      service.index("X12345", GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    internal fun `will call restricted patients if prisoner is outside`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "OUT",
            locationId = 1,
            description = "Outside",
            agencyName = "Outside Prison",
          ),
        ).right(),
      )

      service.index("X12345", GREEN)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    internal fun `will throw exception if restricted patients service call fails`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          assignedLivingUnit = AssignedLivingUnit(
            agencyId = "OUT",
            locationId = 1,
            description = "Outside",
            agencyName = "Outside Prison",
          ),
        ).right(),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index("X12345", GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }

    @Test
    internal fun `will not call incentives if no booking id`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          bookingId = null,
        ).right(),
      )

      service.index("X12345", GREEN)

      verifyNoInteractions(incentivesService)
    }

    @Test
    internal fun `will call incentives service if there is a booking`() {
      whenever(nomisService.getOffender(any())).thenReturn(
        OffenderBookingBuilder().anOffenderBooking().copy(
          bookingId = 12345L,
        ).right(),
      )

      service.index("X12345", GREEN)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    internal fun `will throw exception if incentives service call fails`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index("X12345", GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }
  }
}
