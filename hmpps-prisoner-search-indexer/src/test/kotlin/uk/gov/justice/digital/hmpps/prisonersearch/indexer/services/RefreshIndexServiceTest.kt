package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder

class RefreshIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val nomisService = mock<NomisService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexBuildProperties = IndexBuildProperties(10)
  private val refreshIndexService = RefreshIndexService(
    indexStatusService,
    indexQueueService,
    nomisService,
    prisonerSynchroniserService,
    indexBuildProperties,
  )

  @Nested
  inner class StartIndexRefresh {
    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { refreshIndexService.startIndexRefresh() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build is already BUILDING")

      verify(indexStatusService).getIndexStatus()
    }

    @Test
    fun `Index has active messages returns an error`() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndexState = COMPLETED),
      )
      val expectedIndexQueueStatus = IndexQueueStatus(1, 0, 0)
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(expectedIndexQueueStatus)

      assertThatThrownBy { refreshIndexService.startIndexRefresh() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index has active messages")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      refreshIndexService.startIndexRefresh()

      verify(indexQueueService).sendRefreshIndexMessage()
    }
  }

  @Nested
  inner class RefreshIndex {
    private val indexStatus =
      IndexStatus(currentIndexState = COMPLETED)

    @BeforeEach
    internal fun beforeEach() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing is in progress`() {
      val indexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { refreshIndexService.refreshIndex() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build is already BUILDING")
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(25)

      assertThat(refreshIndexService.refreshIndex()).isEqualTo(3)
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(25)

      refreshIndexService.refreshIndex()

      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(2, 10))
    }

    @Test
    internal fun `will split total list by our page size`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(30)

      refreshIndexService.refreshIndex()

      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(2, 10))
    }

    @Test
    internal fun `will round up last page to page size when over`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(31)

      refreshIndexService.refreshIndex()

      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(2, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(3, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will round up last page to page size when under`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(29)

      refreshIndexService.refreshIndex()
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(0, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(1, 10))
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(2, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will create a large number of pages for a large number of prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(20001)

      refreshIndexService.refreshIndex()
      verify(indexQueueService, times(2001)).sendRefreshPrisonerPageMessage(any())
    }

    @Test
    internal fun `will create a single pages for a tiny number of prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(1)

      refreshIndexService.refreshIndex()
      verify(indexQueueService).sendRefreshPrisonerPageMessage(PrisonerPage(0, 10))
      verifyNoMoreInteractions(indexQueueService)
    }

    @Test
    internal fun `will create no pages for no prisoners`() {
      whenever(nomisService.getTotalNumberOfPrisoners()).thenReturn(0)

      refreshIndexService.refreshIndex()
      verifyNoMoreInteractions(indexQueueService)
    }
  }

  @Nested
  inner class RefreshIndexWithPrisonerPage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndexState = COMPLETED),
      )
      whenever(nomisService.getPrisonerNumbers(any(), any()))
        .thenReturn(listOf("ABC123D", "A12345"))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      refreshIndexService.refreshIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(nomisService).getPrisonerNumbers(page = 99, pageSize = 1000)
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      refreshIndexService.refreshIndexWithPrisonerPage(PrisonerPage(page = 99, pageSize = 1000))

      verify(indexQueueService).sendRefreshPrisonerMessage("ABC123D")
      verify(indexQueueService).sendRefreshPrisonerMessage("A12345")
    }
  }

  @Nested
  inner class RefreshPrisoner {
    @Test
    internal fun `will synchronise offender to all active indices`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any())).thenReturn(booking)

      refreshIndexService.refreshPrisoner("ABC123D")

      verify(prisonerSynchroniserService).refresh(booking)
      verify(nomisService).getOffender("ABC123D")
    }

    @Test
    internal fun `will do nothing if prisoner not in NOMIS`() {
      val indexStatus = IndexStatus(currentIndexState = COMPLETED)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      refreshIndexService.refreshPrisoner("ABC123D")

      verifyNoInteractions(prisonerSynchroniserService)
    }
  }
}
