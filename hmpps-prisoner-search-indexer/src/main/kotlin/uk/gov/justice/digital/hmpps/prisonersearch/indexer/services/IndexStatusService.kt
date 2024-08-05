package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun initialiseIndexWhenRequired(indexStatusId: String, noneValue: SyncIndex): IndexStatusService {
    if (!checkIndexStatusExistsIgnoringMissingRepo(indexStatusId)) {
      indexStatusRepository.save(IndexStatus.newIndex(indexStatusId, noneValue))
        .also { log.info("Created missing index status {}", it) }
    }
    return this
  }

  private fun checkIndexStatusExistsIgnoringMissingRepo(indexStatusId: String): Boolean =
    try {
      indexStatusRepository.existsById(indexStatusId)
    } catch (e: Exception) {
      false
    }

  fun getIndexStatus(indexStatusId: String): IndexStatus = indexStatusRepository.findById(indexStatusId).orElseThrow()

  fun markBuildAbsent(indexStatusId: String): IndexStatus = getIndexStatus(indexStatusId).let { currentIndexStatus ->
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildAbsent())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildInProgress(indexStatusId: String): IndexStatus = getIndexStatus(indexStatusId).let { currentIndexStatus ->
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildCompleteAndSwitchIndex(indexStatusId: String): IndexStatus = getIndexStatus(indexStatusId).let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    } else {
      currentIndexStatus
    }
  }

  fun switchIndex(indexStatusId: String): IndexStatus = getIndexStatus(indexStatusId).let { currentIndexStatus ->
    indexStatusRepository.save(
      (if (currentIndexStatus.inProgress()) currentIndexStatus.toBuildCancelled() else getIndexStatus(indexStatusId))
        .toSwitchIndex(),
    )
  }

  fun markBuildCancelled(indexStatusId: String): IndexStatus = getIndexStatus(indexStatusId).let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    } else {
      currentIndexStatus
    }
  }
}
