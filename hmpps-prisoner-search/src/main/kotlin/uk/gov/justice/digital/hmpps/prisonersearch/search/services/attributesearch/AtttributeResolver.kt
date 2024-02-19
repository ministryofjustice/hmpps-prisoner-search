package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.genericType
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

typealias Attributes = Map<String, KClass<*>>

@Configuration
class AttributeResolver {
  @Bean
  fun attributes(): Attributes = getAttributes(Prisoner::class)
}

internal fun getAttributes(kClass: KClass<*>): Attributes =
  kClass.memberProperties.flatMap { prop -> findTypes(prop) }.toMap()

private fun findTypes(
  prop: KProperty1<*, *>,
  prefix: String = "",
): List<Pair<String, KClass<*>>> =
  when (getPropertyType(prop)) {
    PropertyType.SIMPLE -> listOf("${prefix}${prop.name}" to getPropertyClass(prop))
    PropertyType.LIST -> {
      getGenericTypeClass(prop).memberProperties
        .flatMap { childProp -> findTypes(childProp, "${prefix}${prop.name}.") }
    }
    PropertyType.COMPLEX -> {
      getPropertyClass(prop).memberProperties
        .flatMap { childProp -> findTypes(childProp, "${prefix}${prop.name}.") }
    }
  }

private fun getGenericTypeClass(prop: KProperty1<*, *>) = prop.returnType.genericType()

private fun getPropertyClass(prop: KProperty1<*, *>) = prop.returnType.classifier as KClass<*>

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
