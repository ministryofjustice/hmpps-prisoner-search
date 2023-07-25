package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex

abstract class IndexException(message: String) : RuntimeException(message)
abstract class IndexConflictException(message: String) : IndexException(message)

class BuildAlreadyInProgressException(indexStatus: IndexStatus) : IndexConflictException(startAlreadyMessage(indexStatus))
class BuildInProgressException(indexStatus: IndexStatus) : IndexConflictException(startAlreadyMessage(indexStatus))

class BuildCancelledException(indexStatus: IndexStatus) : IndexConflictException(startStateMessage(indexStatus))
class BuildAbsentException(indexStatus: IndexStatus) : IndexConflictException(startStateMessage(indexStatus))

class BuildNotInProgressException(indexStatus: IndexStatus) : IndexConflictException(endStateMessage(indexStatus))
class WrongIndexRequestedException(indexStatus: IndexStatus) : IndexException(endStateMessage(indexStatus))

class NoActiveIndexesException(indexStatus: IndexStatus) :
  IndexConflictException("Cannot update current index ${indexStatus.currentIndex} which is in state ${indexStatus.currentIndexState} and other index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState}")

class PrisonerNotFoundException(prisonerNumber: String) : IndexException("The prisoner $prisonerNumber could not be found")

class ActiveMessagesExistException(index: SyncIndex, indexQueueStatus: IndexQueueStatus, action: String) :
  IndexConflictException("The index ${index.indexName} has active messages $indexQueueStatus so we cannot process $action")

class ThresholdNotReachedException(index: SyncIndex, threshold: Long) :
  IndexConflictException("The index ${index.indexName} has not reached threshold $threshold so we cannot mark the index as complete")

private fun startAlreadyMessage(indexStatus: IndexStatus) = "The build for ${indexStatus.otherIndex} is already ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
private fun endStateMessage(indexStatus: IndexStatus) = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
private fun startStateMessage(indexStatus: IndexStatus) = "The build for ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (started at ${indexStatus.otherIndexStartBuildTime})"
