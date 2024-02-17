import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.Attributes
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Configuration
class AttributeTypeResolver {
  @Bean
  fun attributes(): Attributes = getAttributes(Prisoner::class)
}

internal fun getAttributes(kClass: KClass<*>): Attributes {
  val map = mutableMapOf<String, AttributeType>()
  kClass.memberProperties.forEach { prop ->
    findTypes(prop, map)
  }
  return map.toMap()
}

private fun findTypes(
  prop: KProperty1<*, *>,
  map: MutableMap<String, AttributeType>,
  prefix: String = "",
) {
  when (getPropertyType(prop)) {
    PropertyType.SIMPLE -> map["${prefix}${prop.name}"] = AttributeType.findByClass(getPropertyClass(prop))
    PropertyType.LIST -> {
      getGenericTypeClass(prop).memberProperties
        .forEach { childProp -> findTypes(childProp, map, "${prefix}${prop.name}.") }
    }
    PropertyType.COMPLEX -> {
      getPropertyClass(prop).memberProperties
        .forEach { childProp -> findTypes(childProp, map, "${prefix}${prop.name}.") }
    }
  }
}

private fun getGenericTypeClass(prop: KProperty1<*, *>) = prop.returnType.arguments.first().type!!.classifier as KClass<*>

private fun getPropertyClass(prop: KProperty1<*, *>) = prop.returnType.classifier as KClass<*>

private enum class PropertyType {
  SIMPLE,
  COMPLEX,
  LIST,
}

private fun getPropertyType(prop: KProperty1<*, *>): PropertyType =
  when (prop.returnType.classifier) {
    List::class -> PropertyType.LIST
    in AttributeType.supportedTypes() -> PropertyType.SIMPLE
    else -> PropertyType.COMPLEX
  }
