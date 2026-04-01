package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

abstract class IndexException(message: String) : RuntimeException(message)
abstract class IndexConflictException(message: String) : IndexException(message)

class PrisonerNotFoundException(prisonerNumber: String) : IndexException("The prisoner $prisonerNumber could not be found")

class ActiveMessagesExistException(indexQueueStatus: IndexQueueStatus, action: String) : IndexConflictException("The index has active messages $indexQueueStatus so we cannot process $action")
