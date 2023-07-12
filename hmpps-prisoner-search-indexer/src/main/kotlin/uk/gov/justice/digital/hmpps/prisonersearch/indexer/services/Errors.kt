package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import uk.gov.justice.digital.hmpps.prisonersearch.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.model.SyncIndex

interface Error {
  fun message(): String
}

interface IndexError : Error
interface PrisonerError : Error

data class BuildAlreadyInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildCancelledError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildAbsentError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The build for ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
}

data class BuildNotInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class WrongIndexRequestedError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class NoActiveIndexesError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "Cannot update current index ${indexStatus.currentIndex} which is in state ${indexStatus.currentIndexState} and other index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState}"
}

data class PrisonerNotFoundError(val prisonerNumber: String) : PrisonerError {
  override fun message() = "The prisoner $prisonerNumber could not be found"
}

data class ActiveMessagesExistError(val index: SyncIndex, val indexQueueStatus: IndexQueueStatus, val action: String) : IndexError {
  override fun message() = "The index ${index.indexName} has active messages $indexQueueStatus so we cannot process $action"
}

data class ThresholdNotReachedError(val index: SyncIndex, val threshold: Long) : IndexError {
  override fun message() = "The index ${index.indexName} has not reached threshold $threshold so we cannot mark the index as complete"
}
