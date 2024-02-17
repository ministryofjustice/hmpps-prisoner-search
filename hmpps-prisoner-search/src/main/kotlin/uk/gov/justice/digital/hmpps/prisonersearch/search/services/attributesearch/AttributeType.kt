package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

typealias Attributes = Map<String, AttributeType>

enum class AttributeType(private val kclass: KClass<*>) {
  STRING(String::class),
  INTEGER(Integer::class),
  BOOLEAN(Boolean::class),
  DATE(LocalDate::class),
  DATE_TIME(LocalDateTime::class);

  companion object {
    fun findType(kclass: KClass<*>): AttributeType = entries.associateBy { it.kclass }[kclass] ?: throw IllegalStateException("Unsupported attribute type: $kclass, this shouldn't happen")
    fun supportedTypes(): List<KClass<*>> = entries.map { it.kclass }
  }
}