package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest

@Schema(description = "A request to search for prisoners by attributes")
data class AttributeSearchRequest(
  @Schema(description = "A query to search for prisoners by attributes")
  val query: Query,
  val pagination: PaginationRequest = PaginationRequest(0, 10),
) {
  fun validate(attributes: Attributes) {
    listOf(query).getAllQueries().forEach { it.validate() }
    listOf(query).getAllTypeMatchers()
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
