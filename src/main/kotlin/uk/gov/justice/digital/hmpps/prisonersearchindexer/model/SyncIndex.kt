package uk.gov.justice.digital.hmpps.prisonersearchindexer.model

enum class SyncIndex(val indexName: String) {

  GREEN("prisoner-search-green"), BLUE("prisoner-search-blue"), NONE("new-indexes");

  fun otherIndex(): SyncIndex = when (this) {
    GREEN -> BLUE
    BLUE -> GREEN
    NONE -> GREEN
  }
}
