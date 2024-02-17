package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntegerMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

typealias Attributes = Map<String, AttributeType>

enum class AttributeType(private val kclass: KClass<*>, private val matcherKclass: KClass<out TypeMatcher>) {
  STRING(String::class, StringMatcher::class),
  INTEGER(Integer::class, IntegerMatcher::class),
  BOOLEAN(Boolean::class, BooleanMatcher::class),
  DATE(LocalDate::class, DateMatcher::class),
  DATE_TIME(LocalDateTime::class, DateTimeMatcher::class),
  ;

  companion object {
    fun supportedTypes(): List<KClass<*>> = entries.map { it.kclass }
    fun findByClass(kclass: KClass<*>): AttributeType =
      entries.associateBy { it.kclass }[kclass]
        ?: throw IllegalStateException("Unsupported attribute type: $kclass, this shouldn't happen")
    fun findByMatcher(matcherKclass: KClass<out TypeMatcher>): AttributeType =
      entries.associateBy { it.matcherKclass }[matcherKclass]
        ?: throw IllegalStateException("Unsupported matcher type: $matcherKclass, this shouldn't happen")
  }
}
