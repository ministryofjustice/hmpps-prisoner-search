package uk.gov.justice.digital.hmpps.prisonersearchindexer.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearchindexer.repository.IndexStatusRepository

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

  fun getIndexStatus(): IndexStatus =
    indexStatusRepository.findById(INDEX_STATUS_ID).orElseThrow()

  fun markBuildInProgress(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress().not()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    }
    return currentIndexStatus
  }

  fun markBuildCompleteAndSwitchIndex(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildComplete().toSwitchIndex())
    }
    return currentIndexStatus
  }

  fun switchIndex(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildCancelled().toSwitchIndex())
    }
    return indexStatusRepository.save(getIndexStatus().toSwitchIndex())
  }

  fun markBuildCancelled(): IndexStatus {
    val currentIndexStatus = getIndexStatus()
    if (currentIndexStatus.inProgress()) {
      return indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    }
    return currentIndexStatus
  }
}
