package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearchindexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex

@Service
class PopulateIndexService(
  private val indexStatusService: IndexStatusService,
  private val prisonerSynchroniserService: PrisonerSynchroniserService,
  private val indexQueueService: IndexQueueService,
  private val maintainIndexService: MaintainIndexService,
  private val telemetryClient: TelemetryClient,
) {
  fun populateIndex(index: SyncIndex): Either<Error, Int> =
    executeAndTrackTimeMillis(TelemetryEvents.BUILD_INDEX_MSG) {
      indexStatusService.getIndexStatus()
        .also { maintainIndexService.logIndexStatuses(it) }
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .failIf({ it.currentIndex.otherIndex() != index }) { WrongIndexRequestedError(it) }
        .map { doPopulateIndex() }
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
    val chunks = prisonerSynchroniserService.splitAllPrisonersIntoChunks()
    chunks.forEach { indexQueueService.sendPopulatePrisonerPageMessage(it) }
    return chunks.size
  }

  fun populateIndexWithPrisonerPage(prisonerPage: PrisonerPage): Either<Error, Unit> =
    executeAndTrackTimeMillis(
      TelemetryEvents.BUILD_PAGE_MSG,
      mapOf("prisonerPage" to prisonerPage.page.toString()),
    ) {
      indexStatusService.getIndexStatus()
        .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
        .flatMap {
          prisonerSynchroniserService.getAllPrisonerNumbersInPage(prisonerPage)
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
            }.right()
        }
    }

  fun populateIndexWithPrisoner(prisonerNumber: String): Either<Error, Prisoner> =
    indexStatusService.getIndexStatus()
      .failIf(IndexStatus::isNotBuilding) { BuildNotInProgressError(it) }
      .flatMap { prisonerSynchroniserService.synchronisePrisoner(prisonerNumber, it.currentIndex.otherIndex()) }

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
}
