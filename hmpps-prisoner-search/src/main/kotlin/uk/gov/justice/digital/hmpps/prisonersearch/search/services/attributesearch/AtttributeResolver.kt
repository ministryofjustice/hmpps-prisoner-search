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

val FUZZY_ATTRIBUTES = listOf(
  "firstName",
  "middleNames",
  "lastName",
  "prisonName",
  "mostSeriousOffence",
  "locationDescription",
  "dischargeHospitalDescription",
  "dischargeDetails",
  "tattoos.comment",
  "scars.comment",
  "marks.comment",
  "aliases.firstName",
  "aliases.middleNames",
  "aliases.lastName",
)

class Attribute(
  val type: KClass<*>,
  val openSearchName: String,
  val isNested: Boolean = false,
  val isFuzzy: Boolean = false,
)

@Configuration
class AttributeResolver {
  @Bean
  fun attributes(): Attributes = getAttributes(Prisoner::class)
}

internal fun getAttributes(kClass: KClass<*>): Attributes =
  kClass.memberProperties.flatMap { prop -> findAttributes(prop) }.toMap()

private fun findAttributes(
  prop: KProperty1<*, *>,
  prefix: String = "",
  nested: Boolean = false,
): List<Pair<String, Attribute>> =
  when (getPropertyType(prop)) {
    PropertyType.SIMPLE -> {
      getPropertyClass(prop).let { propClass ->
        val propertyName = prefix + prop.name
        val openSearchName = prefix + getOpenSearchName(prop, propClass)
        val fuzzy = FUZZY_ATTRIBUTES.contains(propertyName)
        listOf(propertyName to Attribute(propClass, openSearchName, nested, fuzzy))
      }
    }
    PropertyType.LIST -> {
      getGenericTypeClass(prop).memberProperties
        .flatMap { childProp -> findAttributes(childProp, "${prefix}${prop.name}.", nested || prop.hasFieldAnnotation("Nested")) }
    }
    PropertyType.COMPLEX -> {
      getPropertyClass(prop).memberProperties
        .flatMap { childProp -> findAttributes(childProp, "${prefix}${prop.name}.", nested || prop.hasFieldAnnotation("Nested")) }
    }
  }

private fun getGenericTypeClass(prop: KProperty1<*, *>) = prop.returnType.genericType()

private fun getPropertyClass(prop: KProperty1<*, *>) = prop.returnType.classifier as KClass<*>

private fun getOpenSearchName(prop: KProperty1<*, *>, propClass: KClass<*>): String =
  if (propClass.simpleName != "String" || (prop.hasFieldAnnotation("Keyword"))) {
    prop.name
  } else {
    "${prop.name}.keyword"
  }

private fun KProperty1<*, *>.hasFieldAnnotation(fieldType: String): Boolean =
  getFieldType() == fieldType

private fun KProperty1<*, *>.getFieldType(): String? {
  if (javaField?.annotations?.size == 0) return null
  val name = javaField?.annotations?.firstOrNull { it.annotationClass == Field::class }?.let { it as Field }?.type?.name
  return name
}

private enum class PropertyType {
  SIMPLE,
  COMPLEX,
  LIST,
}

private fun getPropertyType(prop: KProperty1<*, *>): PropertyType =
  when (prop.returnType.classifier) {
    List::class -> PropertyType.LIST
    in TypeMatcher.getSupportedTypes() -> PropertyType.SIMPLE
    else -> PropertyType.COMPLEX
  }
