package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType

data class BooleanMatcher(
  override val attribute: String,
  val condition: Boolean,
) : TypeMatcher {
  override fun validate(attributeType: AttributeType) {
    if (attributeType != AttributeType.BOOLEAN) {
      throw AttributeSearchException("Attribute $attribute is not a boolean attribute")
    }
  }
}