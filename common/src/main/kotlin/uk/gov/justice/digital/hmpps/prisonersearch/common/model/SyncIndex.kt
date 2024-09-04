package uk.gov.justice.digital.hmpps.prisonersearch.common.model

enum class SyncIndex(
  val indexName: String,
  val incentiveIndexName: String,
) {

  GREEN("prisoner-search-green", "incentive-search-green"),
  BLUE("prisoner-search-blue", "incentive-search-blue"),
  NONE("new-indexes", "new-incentive-indexes"),
  ;

  fun otherIndex(indexName: String): SyncIndex = when (this) {
    GREEN -> BLUE
    BLUE -> GREEN
    NONE -> GREEN
  }
}

// @Component
// class StringToSyncIndexInterfaceConverter : PropertyValueConverter {
//   override fun write(value: Any): String = (value as SyncIndexInterface).enumName()
//   override fun read(value: Any): SyncIndexInterface = SyncIndex.valueOf(value.toString())
// }
