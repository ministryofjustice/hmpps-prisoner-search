package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerSearchResource
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.IntegerMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.Matcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.TextMatcher
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

enum class AttributeType {
  STRING,
  INTEGER,
  BOOLEAN,
  DATE,
  DATE_TIME,
}

@Component
class AttributeSearchService {

  private val attributeTypes: Map<String, AttributeType> = AttributeTypeResolver().getAttributeTypes(Prisoner::class)

  fun search(request: AttributeSearchRequest) {
    validate(request)
    log.info("searchByAttributes called with request: $request")
  }

  fun validate(request: AttributeSearchRequest) {
    if (request.matchers.isEmpty()) {
      throw AttributeSearchException("At least one matcher must be provided")
    }
    getAllMatchers(request.matchers).forEach { validateMatcher(it) }
    getAllTextMatchers(request.matchers).forEach { it.validate() }
    getAllIntegerMatchers(request.matchers).forEach { it.validate() }
  }

  private fun validateMatcher(matcher: Matcher) {
    if (matcher.textMatchers.isNullOrEmpty() &&
      matcher.booleanMatchers.isNullOrEmpty() &&
      matcher.integerMatchers.isNullOrEmpty() &&
      matcher.dateMatchers.isNullOrEmpty() &&
      matcher.bodyPartMatchers.isNullOrEmpty() &&
      matcher.children.isNullOrEmpty()
    ) {
      throw AttributeSearchException("Matchers must not be empty")
    }
  }

  private fun getAllMatchers(matcher: List<Matcher>): List<Matcher> {
    val allMatchers = mutableListOf<Matcher>()
    matcher.forEach {
      allMatchers.add(it)
      it.children?.let { children -> allMatchers.addAll(getAllMatchers(children)) }
    }
    return allMatchers
  }

  private fun getAllTextMatchers(matcher: List<Matcher>): List<TextMatcher> {
    val allTextMatchers = mutableListOf<TextMatcher>()
    matcher.forEach {
      it.textMatchers?.let { textMatchers -> allTextMatchers.addAll(textMatchers) }
      it.children?.let { children -> allTextMatchers.addAll(getAllTextMatchers(children)) }
    }
    return allTextMatchers
  }

  private fun getAllIntegerMatchers(matcher: List<Matcher>): List<IntegerMatcher> {
    val allIntegerMatchers = mutableListOf<IntegerMatcher>()
    matcher.forEach {
      it.integerMatchers?.let { textMatchers -> allIntegerMatchers.addAll(textMatchers) }
      it.children?.let { children -> allIntegerMatchers.addAll(getAllIntegerMatchers(children)) }
    }
    return allIntegerMatchers
  }

  private fun TextMatcher.validate() {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.STRING) {
          throw AttributeSearchException("Attribute $attribute is not a text attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (searchTerm.isBlank()) {
      throw AttributeSearchException("Attribute $attribute must not have a blank search term")
    }
  }

  private fun IntegerMatcher.validate() {
    attributeTypes[attribute]
      ?.also {
        if (it != AttributeType.INTEGER) {
          throw AttributeSearchException("Attribute $attribute is not an integer attribute")
        }
      }
      ?: throw AttributeSearchException("Unknown attribute: $attribute")

    if (minValue == null && maxValue == null) {
      throw AttributeSearchException("Attribute $attribute must have at least 1 min or max value")
    }

    if (minValue != null && maxValue != null) {
      val min = if (minInclusive) minValue else minValue + 1
      val max = if (maxInclusive) maxValue else maxValue - 1
      if (max < min) {
        throw AttributeSearchException("Attribute $attribute max value $max less than min value $min")
      }
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(PrisonerSearchResource::class.java)
  }
}

private class AttributeTypeResolver {

  fun getAttributeTypes(kClass: KClass<*>): Map<String, AttributeType> {
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
      PropertyType.SIMPLE -> map["${prefix}${prop.name}"] = typeMap[getPropertyClass(prop)]!!
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
      String::class -> PropertyType.SIMPLE
      Integer::class -> PropertyType.SIMPLE
      Boolean::class -> PropertyType.SIMPLE
      LocalDate::class -> PropertyType.SIMPLE
      LocalDateTime::class -> PropertyType.SIMPLE
      else -> PropertyType.COMPLEX
    }

  private val typeMap: Map<KClass<*>, AttributeType> = mapOf(
    String::class to AttributeType.STRING,
    Integer::class to AttributeType.INTEGER,
    Boolean::class to AttributeType.BOOLEAN,
    LocalDate::class to AttributeType.DATE,
    LocalDateTime::class to AttributeType.DATE_TIME,
  )
}

class AttributeSearchException(message: String) : ValidationException(message)
