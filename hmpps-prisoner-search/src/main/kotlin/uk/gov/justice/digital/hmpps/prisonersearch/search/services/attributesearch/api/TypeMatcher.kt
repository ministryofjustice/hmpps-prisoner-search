package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.opensearch.index.query.AbstractQueryBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import kotlin.reflect.KClass
import kotlin.reflect.KType

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = BooleanMatcher::class, name = "Boolean"),
  JsonSubTypes.Type(value = DateMatcher::class, name = "Date"),
  JsonSubTypes.Type(value = DateTimeMatcher::class, name = "DateTime"),
  JsonSubTypes.Type(value = IntMatcher::class, name = "Int"),
  JsonSubTypes.Type(value = StringMatcher::class, name = "String"),
)
sealed interface TypeMatcher<S> {
  val type: String
  val attribute: String
  fun validate() {}
  fun genericType(): KClass<*> = this::class.genericType()
  fun buildQuery(attributes: Attributes): AbstractQueryBuilder<*> = throw NotImplementedError("buildQuery not implemented for ${this::class.simpleName}")

  companion object {
    @JvmStatic
    fun getSupportedTypes() = TypeMatcher::class.sealedSubclasses.map { it.genericType() }
  }
}

private fun KClass<*>.genericType() = this.supertypes.first().genericType()
internal fun KType.genericType() = this.arguments.first().type!!.classifier as KClass<*>
