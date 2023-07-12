package uk.gov.justice.digital.hmpps.prisonersearch.indexer.health

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexStatusService
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@Component
class IndexInfo(
  private val indexStatusService: IndexStatusService,
  private val prisonerRepository: PrisonerRepository,
  private val hmppsQueueService: HmppsQueueService,
) : InfoContributor {

  private val indexQueue by lazy { hmppsQueueService.findByQueueId("index") as HmppsQueue }
  private val indexQueueUrl by lazy { indexQueue.queueUrl }
  private val indexSqsClient by lazy { indexQueue.sqsClient }

  override fun contribute(builder: Info.Builder) {
    try {
      builder.withDetail("index-status", indexStatusService.getIndexStatus())
    } catch (e: Exception) {
      builder.withDetail("index-status", "No status exists yet (${e.message})")
    }
    builder.withDetail(
      "index-size",
      mapOf(
        GREEN to prisonerRepository.count(GREEN),
        BLUE to prisonerRepository.count(BLUE),
      ),
    )
    try {
      builder.withDetail("prisoner-alias", prisonerRepository.prisonerAliasIsPointingAt().joinToString())
    } catch (e: Exception) {
      builder.withDetail("prisoner-alias", "OpenSearch is not available yet")
    }
    builder.withDetail("index-queue-backlog", safeQueueCount())
  }

  private fun safeQueueCount(): String = try {
    indexSqsClient.countMessagesOnQueue(indexQueueUrl).get().toString()
  } catch (ex: Exception) {
    "error retrieving queue count: ${ex.message}"
  }
}
