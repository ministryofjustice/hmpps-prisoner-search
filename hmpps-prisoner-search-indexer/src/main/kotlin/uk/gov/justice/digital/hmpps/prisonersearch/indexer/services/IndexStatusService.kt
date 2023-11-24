package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun initialiseIndexWhenRequired(): IndexStatusService {
    if (!checkIndexStatusExistsIgnoringMissingRepo()) {
      indexStatusRepository.save(IndexStatus.newIndex())
        .also { log.info("Created missing index status {}", it) }
    }
    return this
  }

  private fun checkIndexStatusExistsIgnoringMissingRepo(): Boolean =
    try {
      indexStatusRepository.existsById("STATUS")
    } catch (e: Exception) {
      false
    }

  fun getIndexStatus(): IndexStatus = indexStatusRepository.findById(INDEX_STATUS_ID).orElseThrow()

  fun markBuildAbsent(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildAbsent())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildInProgress(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildCompleteAndSwitchIndex(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    } else {
      currentIndexStatus
    }
  }

  fun switchIndex(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    indexStatusRepository.save(
      (if (currentIndexStatus.inProgress()) currentIndexStatus.toBuildCancelled() else getIndexStatus())
        .toSwitchIndex(),
    )
  }

  fun markBuildCancelled(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    } else {
      currentIndexStatus
    }
  }
}
