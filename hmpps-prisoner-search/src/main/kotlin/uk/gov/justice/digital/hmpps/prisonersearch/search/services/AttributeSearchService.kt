package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerSearchResource
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.IntegerMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.Matchers
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.TextMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.TypeMatcher
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
    listOf(TextMatcher::class, BooleanMatcher::class, IntegerMatcher::class, DateMatcher::class, DateTimeMatcher::class)
      .forEach { matcherClass ->
        getAllMatchers(matcherClass, request.matchers).forEach { matcher -> matcher.validate(attributeTypes) }
      }
  }

  private fun validateMatcher(matchers: Matchers) {
    if (matchers.textMatchers.isNullOrEmpty() &&
      matchers.booleanMatchers.isNullOrEmpty() &&
      matchers.integerMatchers.isNullOrEmpty() &&
      matchers.dateMatchers.isNullOrEmpty() &&
      matchers.dateTimeMatchers.isNullOrEmpty() &&
      matchers.children.isNullOrEmpty()
    ) {
      throw AttributeSearchException("Matchers must not be empty")
    }
  }

  private fun getAllMatchers(matchers: List<Matchers>): List<Matchers> {
    val allMatchers = mutableListOf<Matchers>()
    matchers.forEach {
      allMatchers.add(it)
      it.children?.also { children -> allMatchers.addAll(getAllMatchers(children)) }
    }
    return allMatchers
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : TypeMatcher> getAllMatchers(type: KClass<T>, matchers: List<Matchers>): List<T> {
    val allMatchers = mutableListOf<T>()
    matchers.forEach {
      when(type) {
        TextMatcher::class -> it.textMatchers?.also { textMatchers -> allMatchers.addAll(textMatchers as List<T>) }
        BooleanMatcher::class -> it.booleanMatchers?.also { booleanMatchers -> allMatchers.addAll(booleanMatchers as List<T>) }
        IntegerMatcher::class -> it.integerMatchers?.also { integerMatchers -> allMatchers.addAll(integerMatchers as List<T>) }
        DateMatcher::class -> it.dateMatchers?.also { dateMatchers -> allMatchers.addAll(dateMatchers as List<T>) }
        DateTimeMatcher::class -> it.dateTimeMatchers?.also { dateTimeMatchers -> allMatchers.addAll(dateTimeMatchers as List<T>) }
      }
      it.children?.also { children -> allMatchers.addAll(getAllMatchers(type, children)) }
    }
    return allMatchers
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
