package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
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

  fun populateIndex(index: SyncIndex): Either<Error, Int> =
    executeAndTrackTimeMillis(uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.BUILD_INDEX_MSG) {
      indexStatusService.getIndexStatus()
        .also { maintainIndexService.logIndexStatuses(it) }
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .failIf({ it.currentIndex.otherIndex() != index }) { WrongIndexRequestedError(it) }
        .map { doPopulateIndex() }
    }

  private inline fun <R> executeAndTrackTimeMillis(
    trackEventName: uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents,
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
      .onEach { indexQueueService.sendPopulatePrisonerPageMessage(it) }
      .also {
        telemetryClient.trackEvent(
          uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.POPULATE_PRISONER_PAGES,
          mapOf("totalNumberOfPrisoners" to totalNumberOfPrisoners.toString(), "pageSize" to pageSize.toString()),
        )
      }.size
  }

  fun populateIndexWithPrisonerPage(prisonerPage: PrisonerPage): Either<Error, Unit> =
    executeAndTrackTimeMillis(
      uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.BUILD_PAGE_MSG,
      mapOf("prisonerPage" to prisonerPage.page.toString()),
    ) {
      indexStatusService.getIndexStatus()
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .flatMap {
          nomisService.getPrisonerNumbers(prisonerPage.page, prisonerPage.pageSize)
            .forEachIndexed { index, offenderIdentifier ->
              if (index == 0 || index == prisonerPage.pageSize - 1) {
                telemetryClient.trackEvent(
                  uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.BUILD_PAGE_BOUNDARY,
                  mutableMapOf(
                    "page" to prisonerPage.page.toString(),
                    "IndexOnPage" to index.toString(),
                    "prisonerNumber" to offenderIdentifier,
                  ),
                )
              }
              indexQueueService.sendPopulatePrisonerMessage(offenderIdentifier)
            }.right()
        }
    }

  fun populateIndexWithPrisoner(prisonerNumber: String): Either<Error, Prisoner> =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .flatMap {
        nomisService.getOffender(prisonerNumber)?.let { ob ->
          prisonerSynchroniserService.index(ob, it.currentIndex.otherIndex())
        }?.right() ?: run {
          // can happen if a prisoner is deleted or merged once indexing has started
          telemetryClient.trackPrisonerEvent(BUILD_PRISONER_NOT_FOUND, prisonerNumber)
          PrisonerNotFoundError(prisonerNumber).left()
        }
      }

  private inline fun IndexStatus.failIf(
    check: (IndexStatus) -> Boolean,
    onFail: (IndexStatus) -> Error,
  ): Either<Error, IndexStatus> =
    when (check(this)) {
      false -> this.right()
      true -> onFail(this).left()
    }

  private inline fun Either<Error, IndexStatus>.failIf(
    crossinline check: (IndexStatus) -> Boolean,
    crossinline onFail: (IndexStatus) -> Error,
  ): Either<Error, IndexStatus> =
    when (this.isLeft()) {
      true -> this
      false -> this.flatMap {
        it.failIf(check, onFail)
      }
    }

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

data class PrisonerPage(val page: Int, val pageSize: Int)
