package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.IndexBuildProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.BUILD_PRISONER_NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent

@Service
class PopulateIndexService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val indexQueueService: IndexQueueService,
  private val maintainIndexService: MaintainIndexService,
  private val nomisService: NomisService,
  private val telemetryClient: TelemetryClient,
  indexBuildProperties: IndexBuildProperties,
) {
  private val pageSize = indexBuildProperties.pageSize

  fun populateIndex(index: SyncIndex): Int = executeAndTrackTimeMillis(TelemetryEvents.BUILD_INDEX_MSG) {
    indexStatusService.getIndexStatus()
      .also { maintainIndexService.logIndexStatuses(it) }
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
      .failIf({ it.currentIndex.otherIndex() != index }) { WrongIndexRequestedException(it) }
      .run { doPopulateIndex() }
  }

  private inline fun <R> executeAndTrackTimeMillis(
    trackEventName: TelemetryEvents,
    properties: Map<String, String> = mapOf(),
    block: () -> R,
  ): R {
    val start = System.currentTimeMillis()
    val result = block()
    telemetryClient.trackEvent(
      trackEventName,
      mutableMapOf("messageTimeMs" to (System.currentTimeMillis() - start).toString()).plus(properties),
    )
    return result
  }

  private fun doPopulateIndex(): Int {
    val totalNumberOfPrisoners = nomisService.getTotalNumberOfPrisoners()
    log.info("Splitting $totalNumberOfPrisoners in to pages each of size $pageSize")
    return (1..totalNumberOfPrisoners step pageSize.toLong()).toList()
      .map { PrisonerPage((it / pageSize).toInt(), pageSize) }
      .onEach { indexQueueService.sendPrisonerPageMessage(it) }
      .also {
        telemetryClient.trackEvent(
          TelemetryEvents.POPULATE_PRISONER_PAGES,
          mapOf("totalNumberOfPrisoners" to totalNumberOfPrisoners.toString(), "pageSize" to pageSize.toString()),
        )
      }.size
  }

  fun populateIndexWithPrisonerPage(prisonerPage: PrisonerPage): Unit = executeAndTrackTimeMillis(
    TelemetryEvents.BUILD_PAGE_MSG,
    mapOf("prisonerPage" to prisonerPage.page.toString()),
  ) {
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
      .run {
        nomisService.getPrisonerNumbers(prisonerPage.page, prisonerPage.pageSize)
          .forEachIndexed { index, offenderIdentifier ->
            if (index == 0 || index == prisonerPage.pageSize - 1) {
              telemetryClient.trackEvent(
                TelemetryEvents.BUILD_PAGE_BOUNDARY,
                mutableMapOf(
                  "page" to prisonerPage.page.toString(),
                  "IndexOnPage" to index.toString(),
                  "prisonerNumber" to offenderIdentifier,
                ),
              )
            }
            indexQueueService.sendPopulatePrisonerMessage(offenderIdentifier)
          }
      }
  }

  fun populateIndexWithPrisoner(prisonerNumber: String): Prisoner = indexStatusService.getIndexStatus()
    .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressException(it) }
    .run {
      nomisService.getOffender(prisonerNumber)?.let { ob ->
        prisonerSynchroniserService.index(ob)
      } ?: run {
        // can happen if a prisoner is deleted or merged once indexing has started
        telemetryClient.trackPrisonerEvent(BUILD_PRISONER_NOT_FOUND, prisonerNumber)
        throw PrisonerNotFoundException(prisonerNumber)
      }
    }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> IndexException,
  ): IndexStatus = when (check(this)) {
    false -> this
    true -> throw onFail(this)
  }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class PrisonerPage(val page: Int, val pageSize: Int)
