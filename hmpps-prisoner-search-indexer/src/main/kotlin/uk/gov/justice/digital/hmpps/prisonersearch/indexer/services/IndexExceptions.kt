package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus

abstract class IndexException(message: String) : RuntimeException(message)
abstract class IndexConflictException(message: String) : IndexException(message)

class BuildAlreadyInProgressException(indexStatus: IndexStatus) : IndexConflictException(startAlreadyMessage(indexStatus))

class BuildNotInProgressException(indexStatus: IndexStatus) : IndexConflictException(endStateMessage(indexStatus))
class WrongIndexRequestedException(indexStatus: IndexStatus) : IndexException(endStateMessage(indexStatus))

class NoActiveIndexesException(indexStatus: IndexStatus) : IndexConflictException("Cannot update index which is in state ${indexStatus.currentIndexState}")

class PrisonerNotFoundException(prisonerNumber: String) : IndexException("The prisoner $prisonerNumber could not be found")

class ActiveMessagesExistException(indexQueueStatus: IndexQueueStatus, action: String) : IndexConflictException("The index has active messages $indexQueueStatus so we cannot process $action")

private fun startAlreadyMessage(indexStatus: IndexStatus) = "The build is already ${indexStatus.currentIndexState} (started at ${indexStatus.currentIndexStartBuildTime})"
private fun endStateMessage(indexStatus: IndexStatus) = "The index is in state ${indexStatus.currentIndexState} (ended at ${indexStatus.currentIndexEndBuildTime})"
