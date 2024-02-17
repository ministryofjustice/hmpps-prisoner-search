package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

sealed interface TypeMatcher {
  val attribute: String
  fun validate() {}
}
