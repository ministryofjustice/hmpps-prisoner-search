package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.INDEX_STATUS_ID
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IndexStatusRepository

@Service
class IndexStatusService(private val indexStatusRepository: IndexStatusRepository) {
  fun getIndexStatus(): IndexStatus = indexStatusRepository.findById(INDEX_STATUS_ID).orElseThrow()

  fun markBuildInProgress(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress().not()) {
      indexStatusRepository.save(currentIndexStatus.toBuildInProgress())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildComplete(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildComplete())
    } else {
      currentIndexStatus
    }
  }

  fun markBuildCancelled(): IndexStatus = getIndexStatus().let { currentIndexStatus ->
    if (currentIndexStatus.inProgress()) {
      indexStatusRepository.save(currentIndexStatus.toBuildCancelled())
    } else {
      currentIndexStatus
    }
  }
}
