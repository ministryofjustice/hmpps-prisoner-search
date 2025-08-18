package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import org.opensearch.index.query.AbstractQueryBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

/**
 * A matcher provides a way to search for a specific type of data in OpenSearch.
 */
@Schema(
  description = "Matchers that will be applied to this query",
  discriminatorMapping = [
    DiscriminatorMapping(value = "Boolean", schema = BooleanMatcher::class),
    DiscriminatorMapping(value = "Date", schema = DateMatcher::class),
    DiscriminatorMapping(value = "DateTime", schema = DateTimeMatcher::class),
    DiscriminatorMapping(value = "Int", schema = IntMatcher::class),
    DiscriminatorMapping(value = "PNC", schema = PncMatcher::class),
    DiscriminatorMapping(value = "String", schema = StringMatcher::class),
  ],
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = BooleanMatcher::class, name = "Boolean"),
  JsonSubTypes.Type(value = DateMatcher::class, name = "Date"),
  JsonSubTypes.Type(value = DateTimeMatcher::class, name = "DateTime"),
  JsonSubTypes.Type(value = IntMatcher::class, name = "Int"),
  JsonSubTypes.Type(value = StringMatcher::class, name = "String"),
  JsonSubTypes.Type(value = PncMatcher::class, name = "PNC"),
)
interface Matcher {
  fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> = throw NotImplementedError("buildQuery not implemented for ${this::class.simpleName}")
}

/**
 * Get matchers for attributes that are properties of objects, e.g. Address.fullAddress is nested.
 *
 * We need to know about these because OpenSearch handles them differently.
 */
internal fun List<Matcher>.findNestedMatchers(attributes: Attributes) = filterIsInstance(TypeMatcher::class.java)
  .filter {
    attributes[it.attribute]?.isNested
      ?: throw AttributeSearchException("Unknown attribute: ${it.attribute}")
  }
