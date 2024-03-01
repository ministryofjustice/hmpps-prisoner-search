package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest

@Schema(description = "A request to search for prisoners by attributes")
data class AttributeSearchRequest(
  @Schema(description = "The type of join to use when combining the matchers and subQueries", example = "AND", defaultValue = "AND")
  val joinType: JoinType = JoinType.AND,
  @Schema(description = "A list of queries of type Query that will be combined with the matchers in this query")
  val queries: List<Query> = listOf(),
  val pagination: PaginationRequest = PaginationRequest(0, 10),
) {
  fun validate(attributes: Attributes) {
    if (queries.isEmpty()) {
      throw AttributeSearchException("Query must not be empty")
    }
    getAllQueries().forEach { it.validate() }
    getAllTypeMatchers()
      .forEach {
        it.validateType(attributes)
        it.validate()
      }
  }

  override fun toString(): String =
    if (queries.size > 1) {
      queries.joinToString(" ${joinType.name} ") { "($it)" }
    } else if (queries.size == 1) {
      queries[0].toString()
    } else {
      ""
    }

  private fun TypeMatcher<*>.validateType(attributes: Attributes) {
    val attributeType = attributes[attribute] ?: throw AttributeSearchException("Unknown attribute: $attribute")
    val genericType = this.genericType()
    if (genericType != attributeType) {
      throw AttributeSearchException("Attribute $attribute of type $attributeType not supported by $genericType matcher")
    }
  }
}

fun AttributeSearchRequest.getAllQueries(): List<Query> = queries.getAllQueries()

fun AttributeSearchRequest.getAllTypeMatchers(): List<TypeMatcher<*>> = queries.getAllTypeMatchers()
