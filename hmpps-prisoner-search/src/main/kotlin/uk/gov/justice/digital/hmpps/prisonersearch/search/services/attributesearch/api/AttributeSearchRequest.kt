package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

data class AttributeSearchRequest(
  val matchers: List<Matchers>,
) {
  fun validate(attributes: Attributes) {
    if (matchers.isEmpty()) {
      throw AttributeSearchException("At least one matcher must be provided")
    }
    matchers.getAllMatchers().forEach { it.validate() }
    matchers.getAllTypeMatchers()
      .forEach {
        it.validateType(attributes)
        it.validate()
      }
  }

  private fun TypeMatcher<*>.validateType(attributes: Attributes) {
    val attributeType = attributes[attribute] ?: throw AttributeSearchException("Unknown attribute: $attribute")
    val genericType = this.genericType()
    if (genericType != attributeType) {
      throw AttributeSearchException("Attribute $attribute of type $attributeType not supported by $genericType matcher")
    }
  }
}
