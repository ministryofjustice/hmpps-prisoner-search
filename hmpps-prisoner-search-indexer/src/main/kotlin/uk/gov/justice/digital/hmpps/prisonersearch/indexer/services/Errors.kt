package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexStatus

interface Error {
  fun message(): String
}

interface IndexError : Error
interface PrisonerError : Error

data class BuildNotInProgressError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class WrongIndexRequestedError(val indexStatus: IndexStatus) : IndexError {
  override fun message() = "The index ${indexStatus.otherIndex} is in state ${indexStatus.otherIndexState} (ended at ${indexStatus.otherIndexEndBuildTime})"
}

data class PrisonerNotFoundError(val prisonerNumber: String) : PrisonerError {
  override fun message() = "The prisoner $prisonerNumber could not be found"
}
