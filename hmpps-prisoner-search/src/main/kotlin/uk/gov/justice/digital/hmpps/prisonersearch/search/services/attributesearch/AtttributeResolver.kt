package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.annotations.Field
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.genericType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

typealias Attributes = Map<String, Attribute>

@Configuration
class AttributeResolver {
  // attributes contains everything you can use in queries in the attributes search
  @Bean
  fun attributes(): Attributes = getAttributes(Prisoner::class)

  // attributesAndObjects contains everything you can request to be populated on the Prisoner record
  @Bean
  fun attributeAndObjects(attributes: Attributes): List<String> = attributes.attributeAndObjects()

  @Bean
  fun requestedAttributeValidator(attributesAndObjects: List<String>): RequestedAttributeValidator = RequestedAttributeValidator(attributesAndObjects)
}

class RequestedAttributeValidator(private val attributesAndObjects: List<String> = emptyList<String>()) {
  fun findMissing(requestedAttributes: List<String>) = requestedAttributes.filterNot { it in attributesAndObjects }
}

// Add each object to the list of attributes, e.g. "currentIncentive.code" -> listOf("currentIncentive", "currentIncentive.code")
internal fun Attributes.attributeAndObjects(): List<String> = this.keys
  .flatMap { attribute ->
    attribute.split(".").let { parts ->
      parts.mapIndexed { index, _ -> parts.take(index + 1).joinToString(".") }
    }
  }
  .toCollection(LinkedHashSet())
  .toList()

/**
 * Derive all attributes that can be searched for in queries
 */
internal fun getAttributes(kClass: KClass<*>): Attributes = kClass.memberProperties.flatMap { prop -> findAttributes(prop) }.toMap()

private fun findAttributes(
  prop: KProperty1<*, *>,
  prefix: String = "",
  nested: Boolean = false,
): List<Pair<String, Attribute>> = when (prop.getPropertyType()) {
  // We've found a type supported by a matcher - create an attribute
  PropertyType.SUPPORTED_TYPE -> {
    val propertyClass = prop.getPropertyClass()
    val propertyName = prefix + prop.name
    val openSearchName = prefix + prop.getOpenSearchName()
    val fuzzy = propertyName.isFuzzyAttribute()
    listOf(propertyName to Attribute(propertyClass, openSearchName, nested, fuzzy))
  }
  // Prevent lists of supported types as they are not extensible
  PropertyType.LIST_SUPPORTED_TYPES -> throw InvalidAttributeTypeException("Attribute '${prefix}${prop.name}' is invalid. Lists of simple types are not supported because they cannot be extended. Please change to a list of objects. See [Prisoner.emailAddresses] for an example.")
  // We've found a list of objects - use recursion with this method to derive the object's attributes
  PropertyType.LIST_OBJECTS -> {
    prop.getGenericTypeClass().memberProperties
      .flatMap { childProp ->
        findAttributes(
          childProp,
          "${prefix}${prop.name}.",
          nested || prop.hasFieldAnnotation("Nested"),
        )
      }
  }
  // We've found a nested object - use recursion with this method to derive the object's attributes
  PropertyType.OBJECT -> {
    prop.getPropertyClass().memberProperties
      .flatMap { childProp ->
        findAttributes(
          childProp,
          "${prefix}${prop.name}.",
          nested || prop.hasFieldAnnotation("Nested"),
        )
      }
  }
}

// Get the generic type of a list, e.g. List<OffenderIdentifier> -> OffenderIdentifier
private fun KProperty1<*, *>.getGenericTypeClass() = returnType.genericType()

// Get the type of a property, e.g. String, List, or a custom class
private fun KProperty1<*, *>.getPropertyClass() = returnType.classifier as KClass<*>

// Get the name of a property as used by OpenSearch, e.g. firstName -> firstName.keyword
private fun KProperty1<*, *>.getOpenSearchName(): String = if (getPropertyClass().simpleName != "String" || (hasFieldAnnotation("Keyword"))) {
  name
} else {
  "$name.keyword"
}

// Checks if a property has an annotation of the passed type
private fun KProperty1<*, *>.hasFieldAnnotation(fieldType: String): Boolean = getFieldType() == fieldType

private fun KProperty1<*, *>.getFieldType(): String? {
  if (javaField?.annotations?.size == 0) return null
  val name = javaField?.annotations?.firstOrNull { it.annotationClass == Field::class }?.let { it as Field }?.type?.name
  return name
}

// The type of a property on the Prisoner record, used to derive searchable attributes
private enum class PropertyType {
  // A type that is supported because it has a dedicated matcher, e.g. DateTime
  SUPPORTED_TYPE,

  // An object type that won't have its own matcher, e.g. Address
  OBJECT,

  // A list of supported types - used only for validation to prevent people adding such lists to the Prisoner record
  LIST_SUPPORTED_TYPES,

  // A list of objects that won't have its own matcher, e.g. List<Address>
  LIST_OBJECTS,
}

private fun KProperty1<*, *>.getPropertyType(): PropertyType = when (returnType.classifier) {
  List::class -> {
    when (this.getGenericTypeClass()) {
      in TypeMatcher.getSupportedTypes() -> PropertyType.LIST_SUPPORTED_TYPES
      else -> PropertyType.LIST_OBJECTS
    }
  }

  in TypeMatcher.getSupportedTypes() -> PropertyType.SUPPORTED_TYPE
  else -> PropertyType.OBJECT
}

class InvalidAttributeTypeException(message: String) : RuntimeException(message)
