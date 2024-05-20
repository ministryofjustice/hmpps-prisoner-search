package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * A type matcher provides a way to search in OpenSearch for a specific attribute type, e.g. DateMatcher searches for dates .
 */
sealed interface TypeMatcher<S> : Matcher {
  val attribute: String
  fun validate() {}
  fun genericType(): KClass<*> = this::class.genericType()

  companion object {
    @JvmStatic
    fun getSupportedTypes() = TypeMatcher::class.sealedSubclasses.map { it.genericType() }
  }

  fun validateType(attributes: Attributes) {
    val attributeType = attributes[attribute]?.type ?: throw AttributeSearchException("Unknown attribute: $attribute")
    val genericType = this.genericType()
    if (genericType != attributeType) {
      throw AttributeSearchException("Attribute $attribute of type $attributeType not supported by $genericType matcher")
    }
  }
}

private fun KClass<*>.genericType() = this.supertypes.first().genericType()
internal fun KType.genericType() = this.arguments.first().type!!.classifier as KClass<*>
