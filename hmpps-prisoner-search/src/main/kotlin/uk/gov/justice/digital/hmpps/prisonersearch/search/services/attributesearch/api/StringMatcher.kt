package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType

data class StringMatcher(
  override val attribute: String,
  val condition: TextCondition,
  val searchTerm: String,
) : TypeMatcher {
  override fun validate(attributeType: AttributeType) {
    if (attributeType != AttributeType.STRING) {
      throw AttributeSearchException("Attribute $attribute is not a text attribute")
    }

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