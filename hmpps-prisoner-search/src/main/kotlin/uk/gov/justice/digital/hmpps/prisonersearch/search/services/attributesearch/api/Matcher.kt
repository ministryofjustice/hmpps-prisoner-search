package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.opensearch.index.query.AbstractQueryBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes

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
  val type: String
  fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> = throw NotImplementedError("buildQuery not implemented for ${this::class.simpleName}")
}
