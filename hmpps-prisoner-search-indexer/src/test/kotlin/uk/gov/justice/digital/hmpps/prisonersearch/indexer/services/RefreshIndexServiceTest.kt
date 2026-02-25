package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_ACTIVE_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.listeners.IndexRequestType.REFRESH_PRISONER_PAGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.nomisprisoner.model.RootOffenderIdRange

class RefreshIndexServiceTest {

  private val indexStatusService = mock<IndexStatusService>()
  private val indexQueueService = mock<IndexQueueService>()
  private val nomisService = mock<NomisService>()
  private val nomisPrisonerService = mock<NomisPrisonerService>()
  private val prisonerSynchroniserService = mock<PrisonerSynchroniserService>()
  private val indexBuildProperties = IndexBuildProperties(10)
  private val refreshIndexService = RefreshIndexService(
    indexStatusService,
    indexQueueService,
    nomisService,
    nomisPrisonerService,
    prisonerSynchroniserService,
    indexBuildProperties,
  )

  @Nested
  inner class StartIndexRefresh {
    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { refreshIndexService.startFullIndexRefresh() }
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

      assertThatThrownBy { refreshIndexService.startFullIndexRefresh() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index has active messages")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      refreshIndexService.startFullIndexRefresh()

      verify(indexQueueService).sendIndexMessage(REFRESH_INDEX)
    }
  }

  @Nested
  inner class StartActiveIndexRefresh {
    @Test
    fun `Index already building returns error`() {
      val expectedIndexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(expectedIndexStatus)

      assertThatThrownBy { refreshIndexService.startActiveIndexRefresh() }
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

      assertThatThrownBy { refreshIndexService.startActiveIndexRefresh() }
        .isInstanceOf(ActiveMessagesExistException::class.java)
        .hasMessageContaining("The index has active messages")
    }

    @Test
    fun `A request is made to mark the index build is in progress`() {
      whenever(indexQueueService.getIndexQueueStatus()).thenReturn(IndexQueueStatus(0, 0, 0))

      whenever(indexStatusService.getIndexStatus())
        .thenReturn(IndexStatus(currentIndexState = COMPLETED))

      refreshIndexService.startActiveIndexRefresh()

      verify(indexQueueService).sendIndexMessage(REFRESH_ACTIVE_INDEX)
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
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(active = eq(false), size = any())).thenReturn(
        listOf(
          RootOffenderIdRange(1, 3),
          RootOffenderIdRange(3, 5),
          RootOffenderIdRange(5, 7),
        ),
      )

      assertThat(refreshIndexService.refreshIndex()).isEqualTo(3)
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(active = eq(false), size = any())).thenReturn(
        listOf(
          RootOffenderIdRange(1, 3),
          RootOffenderIdRange(3, 5),
          RootOffenderIdRange(5, 7),
        ),
      )

      refreshIndexService.refreshIndex()

      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(1, 3), REFRESH_PRISONER_PAGE)
      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(3, 5), REFRESH_PRISONER_PAGE)
      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(5, 7), REFRESH_PRISONER_PAGE)
    }

    @Test
    internal fun `will create no pages for no prisoners`() {
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(eq(false), any())).thenReturn(emptyList())

      refreshIndexService.refreshIndex()
      verifyNoMoreInteractions(indexQueueService)
    }
  }

  @Nested
  inner class RefreshActiveIndex {
    private val indexStatus = IndexStatus(currentIndexState = COMPLETED)

    @BeforeEach
    internal fun beforeEach() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)
    }

    @Test
    internal fun `will return an error if indexing is in progress`() {
      val indexStatus = IndexStatus(currentIndexState = BUILDING)
      whenever(indexStatusService.getIndexStatus()).thenReturn(indexStatus)

      assertThatThrownBy { refreshIndexService.refreshActiveIndex() }
        .isInstanceOf(BuildAlreadyInProgressException::class.java)
        .hasMessageContaining("The build is already BUILDING")
    }

    @Test
    internal fun `will return the number of chunks sent for processing`() {
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(eq(true), any())).thenReturn(
        listOf(
          RootOffenderIdRange(1, 3),
          RootOffenderIdRange(3, 5),
          RootOffenderIdRange(5, 7),
        ),
      )

      assertThat(refreshIndexService.refreshActiveIndex()).isEqualTo(3)
    }

    @Test
    internal fun `For each chunk should send a process chunk message`() {
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(eq(true), any())).thenReturn(
        listOf(
          RootOffenderIdRange(1, 3),
          RootOffenderIdRange(3, 5),
          RootOffenderIdRange(5, 7),
        ),
      )

      refreshIndexService.refreshActiveIndex()

      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(1, 3), REFRESH_ACTIVE_PRISONER_PAGE)
      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(3, 5), REFRESH_ACTIVE_PRISONER_PAGE)
      verify(indexQueueService).sendRootOffenderIdPageMessage(RootOffenderIdPage(5, 7), REFRESH_ACTIVE_PRISONER_PAGE)
    }

    @Test
    internal fun `will create no pages for no prisoners`() {
      whenever(nomisPrisonerService.getAllPrisonersIdRanges(eq(true), any())).thenReturn(emptyList())

      refreshIndexService.refreshActiveIndex()
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
      whenever(nomisPrisonerService.getPrisonNumbers(eq(false), any(), any()))
        .thenReturn(listOf("ABC123D", "A12345"))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      refreshIndexService.refreshIndexWithRootOffenderIdPage(RootOffenderIdPage(99, 1000))

      verify(nomisPrisonerService).getPrisonNumbers(active = false, fromRootOffenderId = 99, toRootOffenderId = 1000)
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      refreshIndexService.refreshIndexWithRootOffenderIdPage(RootOffenderIdPage(99, 1000))

      verify(indexQueueService).sendRefreshPrisonerMessage("ABC123D")
      verify(indexQueueService).sendRefreshPrisonerMessage("A12345")
    }
  }

  @Nested
  inner class RefreshActiveIndexWithRootOffenderIdPage {
    @BeforeEach
    internal fun setUp() {
      whenever(indexStatusService.getIndexStatus()).thenReturn(
        IndexStatus(currentIndexState = COMPLETED),
      )
      whenever(nomisPrisonerService.getPrisonNumbers(eq(true), any(), any()))
        .thenReturn(listOf("ABC123D", "ABC123E", "ABC123F"))
    }

    @Test
    internal fun `will get offenders in the supplied page`() {
      refreshIndexService.refreshActiveIndexWithRootOffenderIdPage(RootOffenderIdPage(fromRootOffenderId = 5, toRootOffenderId = 10))

      verify(nomisPrisonerService).getPrisonNumbers(active = true, fromRootOffenderId = 5, toRootOffenderId = 10)
    }

    @Test
    internal fun `for each offender will send populate offender message`() {
      refreshIndexService.refreshActiveIndexWithRootOffenderIdPage(RootOffenderIdPage(fromRootOffenderId = 5, toRootOffenderId = 10))

      verify(indexQueueService).sendRefreshPrisonerMessage("ABC123D")
      verify(indexQueueService).sendRefreshPrisonerMessage("ABC123E")
      verify(indexQueueService).sendRefreshPrisonerMessage("ABC123F")
    }
  }

  @Nested
  inner class RefreshPrisoner {
    @Test
    internal fun `will synchronise offender to all active indices`() {
      val booking = OffenderBookingBuilder().anOffenderBooking()
      whenever(nomisService.getOffender(any<String>())).thenReturn(booking)

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
