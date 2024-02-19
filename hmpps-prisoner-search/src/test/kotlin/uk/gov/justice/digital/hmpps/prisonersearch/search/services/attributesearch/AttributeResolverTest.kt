package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Stream
import kotlin.reflect.KClass

class AttributeResolverTest {
  val attributes = getAttributes(Root::class)

  private class Root(
    val string: String,
    val boolean: Boolean,
    val integer: Int,
    val localDate: LocalDate,
    val localDateTime: LocalDateTime,
    val list: List<Complex>,
    val complex: Complex,
  )

  private class Complex(
    val string: String,
    val boolean: Boolean,
    val integer: Int,
    val localDate: LocalDate,
    val localDateTime: LocalDateTime,
    val nested: Nested,
  )

  private class Nested(
    val code: String,
    val description: String,
  )

  companion object {
    @JvmStatic
    fun testParameters() = Stream.of(
      of("string", String::class),
      of("boolean", Boolean::class),
      of("integer", Integer::class),
      of("localDate", LocalDate::class),
      of("localDateTime", LocalDateTime::class),
      of("list.string", String::class),
      of("list.boolean", Boolean::class),
      of("list.integer", Integer::class),
      of("list.localDate", LocalDate::class),
      of("list.localDateTime", LocalDateTime::class),
      of("list.nested.code", String::class),
      of("list.nested.description", String::class),
      of("complex.string", String::class),
      of("complex.boolean", Boolean::class),
      of("complex.integer", Integer::class),
      of("complex.localDate", LocalDate::class),
      of("complex.localDateTime", LocalDateTime::class),
      of("complex.nested.code", String::class),
      of("complex.nested.description", String::class),
    )
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  fun `should resolve attributes`(attributeName: String, type: KClass<*>) {
    assertThat(attributes[attributeName]).isEqualTo(type)
  }

  @Test
  fun `should not resolve extra attributes`() {
    assertThat(attributes.size).isEqualTo(19)
  }
}
