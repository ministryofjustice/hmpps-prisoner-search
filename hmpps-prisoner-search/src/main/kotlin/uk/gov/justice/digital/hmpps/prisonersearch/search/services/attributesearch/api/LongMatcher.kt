package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

// Currently a dummy class to support tests (no Long field yet used to match documents)
@Suppress("unused")
data class LongMatcher(override val attribute: String) : TypeMatcher<Long> {
  override val type: String = "Long"
}
