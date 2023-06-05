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
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.PrisonerRepository

internal class PrisonerSynchroniserServiceTest {
  private val nomisService = mock<NomisService>()
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val service = PrisonerSynchroniserService(prisonerRepository, telemetryClient, nomisService, restrictedPatientService, incentivesService, IndexBuildProperties(10, 0))

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

  @Nested
  inner class CheckExistsAndReset {
    @Nested
    inner class IndexExists {
      @BeforeEach
      internal fun setUp() {
        whenever(prisonerRepository.doesIndexExist(GREEN))
          .thenReturn(true)
          .thenReturn(false)

        service.checkExistsAndReset(GREEN)
      }

      @Test
      internal fun `will delete the index if it exists`() {
        verify(prisonerRepository).deleteIndex(GREEN)
      }

      @Test
      internal fun `will recreate the index`() {
        verify(prisonerRepository).createIndex(GREEN)
      }
    }

    @Nested
    inner class IndexDoesNotExists {
      @BeforeEach
      internal fun setUp() {
        whenever(prisonerRepository.doesIndexExist(GREEN))
          .thenReturn(false)
          .thenReturn(false)

        service.checkExistsAndReset(GREEN)
      }

      @Test
      internal fun `won't bother deleting index if it does not exist`() {
        whenever(prisonerRepository.doesIndexExist(GREEN)).thenReturn(false)

        service.checkExistsAndReset(GREEN)

        verify(prisonerRepository, never()).deleteIndex(any())
      }

      @Test
      internal fun `will create the index`() {
        verify(prisonerRepository).createIndex(GREEN)
      }
    }

    @Nested
    inner class IndexDeleteIsSlow {

      @Test
      fun `waits for index to be deleted before recreating`() {
        whenever(prisonerRepository.doesIndexExist(GREEN))
          .thenReturn(true)
          .thenReturn(true)
          .thenReturn(false)

        service.checkExistsAndReset(GREEN)

        verify(prisonerRepository, times(3)).doesIndexExist(GREEN)
        verify(prisonerRepository).deleteIndex(GREEN)
        verify(prisonerRepository).createIndex(GREEN)
      }
    }
  }

  @Nested
  inner class SplitAllPrisonersIntoChunks {
    @Test
    internal fun `will split total list by our page size`() {
      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 30))

      val chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).containsExactly(
        PrisonerPage(0, 10),
        PrisonerPage(1, 10),
        PrisonerPage(2, 10),
      )
    }

    @Test
    internal fun `will round up last page to page size`() {
      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 31))

      var chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).containsExactly(
        PrisonerPage(0, 10),
        PrisonerPage(1, 10),
        PrisonerPage(2, 10),
        PrisonerPage(3, 10),
      )

      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 29))

      chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).containsExactly(
        PrisonerPage(0, 10),
        PrisonerPage(1, 10),
        PrisonerPage(2, 10),
      )
    }

    @Test
    internal fun `will create a large number of pages for a large number of prisoners`() {
      val service = PrisonerSynchroniserService(
        prisonerRepository,
        telemetryClient,
        nomisService,
        restrictedPatientService,
        incentivesService,
        IndexBuildProperties(1000, 0),
      )

      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 2000001))

      val chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).hasSize(2001)
    }

    @Test
    internal fun `will create a single pages for a tiny number of prisoners`() {
      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 1))

      val chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).hasSize(1)
    }

    @Test
    internal fun `will send a telemetry event`() {
      val service = PrisonerSynchroniserService(
        prisonerRepository,
        telemetryClient,
        nomisService,
        restrictedPatientService,
        incentivesService,
        IndexBuildProperties(1000, 0),
      )

      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 1))

      service.splitAllPrisonersIntoChunks()

      verify(telemetryClient).trackEvent(TelemetryEvents.POPULATE_PRISONER_PAGES.name, mapOf("totalNumberOfPrisoners" to "1", "pageSize" to "1000"), null)
    }

    @Test
    internal fun `will create no pages for no prisoners`() {
      val service = PrisonerSynchroniserService(
        prisonerRepository,
        telemetryClient,
        nomisService,
        restrictedPatientService,
        incentivesService,
        IndexBuildProperties(1000, 0),
      )

      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(OffenderResponse(totalRows = 0))

      val chunks = service.splitAllPrisonersIntoChunks()
      assertThat(chunks).hasSize(0)
    }
  }

  @Nested
  inner class GetAllPrisonerIdentifiersInPage {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisService.getOffendersIds(any(), any())).thenReturn(
        OffenderResponse(
          listOf(
            OffenderId("X12345"),
            OffenderId("X12346"),
            OffenderId("X12347"),
            OffenderId("X12348"),
          ),
          totalRows = 1,
        ),
      )
    }

    @Test
    internal fun `will pass through page numbers`() {
      service.getAllPrisonerNumbersInPage(PrisonerPage(3, 1000))
      verify(nomisService).getOffendersIds(3, 1000)
    }

    @Test
    internal fun `will map each identifier`() {
      val prisoners = service.getAllPrisonerNumbersInPage(PrisonerPage(3, 1000))

      assertThat(prisoners).containsExactly(
        OffenderId("X12345"),
        OffenderId("X12346"),
        OffenderId("X12347"),
        OffenderId("X12348"),
      )
    }
  }
}
