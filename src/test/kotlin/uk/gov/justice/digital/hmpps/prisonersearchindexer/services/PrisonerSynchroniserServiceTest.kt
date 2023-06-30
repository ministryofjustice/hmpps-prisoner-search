package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository

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
  inner class SynchronisePrisoner {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisService.getOffender(any()))
        .thenReturn(
          OffenderBookingBuilder().anOffenderBooking().right(),
        )
    }

    @Test
    fun `should retrieve prisoner`() {
      service.synchronisePrisoner("X12345", GREEN)

      verify(nomisService).getOffender("X12345")
    }

    @Test
    internal fun `will save prisoner to repository`() {
      service.synchronisePrisoner("X12345", GREEN)

      verify(prisonerRepository).save(isA(), isA())
    }

    @Test
    internal fun `will save prisoner to current index`() {
      service.synchronisePrisoner("X12345", GREEN)

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    internal fun `will send telemetry event for update`() {
      service.synchronisePrisoner("X12345", GREEN)

      verify(telemetryClient).trackEvent(eq("PRISONER_UPDATED"), any(), isNull())
    }
  }
}
