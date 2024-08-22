package uk.gov.justice.digital.hmpps.prisonersearch.common.model

enum class SyncIndex(val indexName: String) {

  GREEN("prisoner-search-green"),
  BLUE("prisoner-search-blue"),
  NONE("new-indexes"),
  RED("prisoner-search"),
  ;

  fun otherIndex(): SyncIndex = when (this) {
    GREEN -> BLUE
    BLUE -> GREEN
    NONE -> GREEN
    RED -> RED
  }
}
