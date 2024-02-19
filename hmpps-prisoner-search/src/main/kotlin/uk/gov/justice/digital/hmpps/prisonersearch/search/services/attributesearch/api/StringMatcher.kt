package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

data class StringMatcher(
  override val attribute: String,
  val condition: TextCondition,
  val searchTerm: String,
) : TypeMatcher<String> {
  override fun validate() {
    if (searchTerm.isBlank()) {
      throw AttributeSearchException("Attribute $attribute must not have a blank search term")
    }
  }
}

enum class TextCondition {
  IS,
  IS_NOT,
  CONTAINS,
}
