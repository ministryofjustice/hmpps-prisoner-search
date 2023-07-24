package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.server.ResponseStatusException
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex

class BuildAlreadyInProgressException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")

class BuildInProgressException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")

class BuildCancelledException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "The build for ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")

class BuildAbsentException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "The build for ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})")

class BuildNotInProgressException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})")

class WrongIndexRequestedException(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

class NoActiveIndexesException(indexStatus: IndexStatus) :
  ResponseStatusException(CONFLICT, "Cannot update current index ${indexStatus.currentIndex} which is in state ${indexStatus.currentIndexState} and other index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState}")

class PrisonerNotFoundException(val prisonerNumber: String) :
  ResponseStatusException(NOT_FOUND, "The prisoner $prisonerNumber could not be found")

class ActiveMessagesExistException(index: SyncIndex, indexQueueStatus: IndexQueueStatus, action: String) :
  ResponseStatusException(CONFLICT, "The index ${index.indexName} has active messages $indexQueueStatus so we cannot process $action")

class ThresholdNotReachedException(index: SyncIndex, threshold: Long) :
  ResponseStatusException(CONFLICT, "The index ${index.indexName} has not reached threshold $threshold so we cannot mark the index as complete")
