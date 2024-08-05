package uk.gov.justice.digital.hmpps.prisonersearch.common.model

enum class SyncIndex(val indexName: String) {

  GREEN("prisoner-search-green"),
  BLUE("prisoner-search-blue"),
  NONE("new-indexes"),

  GREEN_I("incentive-search-green"),
  BLUE_I("incentive-search-blue"),
  NONE_I("new-incentive-indexes"),
  ;

  fun otherIndex(indexName: String): SyncIndex = when (this) {
    GREEN -> BLUE
    BLUE -> GREEN
    NONE -> GREEN

    GREEN_I -> BLUE_I
    BLUE_I -> GREEN_I
    NONE_I -> GREEN_I
  }

  fun indexName() = indexName
  fun enumName() = name
}

// @Component
// class StringToSyncIndexInterfaceConverter : PropertyValueConverter {
//   override fun write(value: Any): String = (value as SyncIndexInterface).enumName()
//   override fun read(value: Any): SyncIndexInterface = SyncIndex.valueOf(value.toString())
// }
