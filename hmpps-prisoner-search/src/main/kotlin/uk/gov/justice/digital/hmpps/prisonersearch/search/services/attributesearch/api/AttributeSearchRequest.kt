package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

data class AttributeSearchRequest(
  val queries: List<Query>,
) {
  fun validate(attributes: Attributes) {
    if (queries.isEmpty()) {
      throw AttributeSearchException("At least one matcher must be provided")
    }
    queries.getAllQueries().forEach { it.validate() }
    queries.getAllTypeMatchers()
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
