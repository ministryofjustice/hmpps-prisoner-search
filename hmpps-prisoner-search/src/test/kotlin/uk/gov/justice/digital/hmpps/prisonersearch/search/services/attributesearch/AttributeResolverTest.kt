package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
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
    fun testParameters(): Stream<Arguments> = Stream.of(
      arguments("string", String::class),
      arguments("boolean", Boolean::class),
      arguments("integer", Integer::class),
      arguments("localDate", LocalDate::class),
      arguments("localDateTime", LocalDateTime::class),
      arguments("list.string", String::class),
      arguments("list.boolean", Boolean::class),
      arguments("list.integer", Integer::class),
      arguments("list.localDate", LocalDate::class),
      arguments("list.localDateTime", LocalDateTime::class),
      arguments("list.nested.code", String::class),
      arguments("list.nested.description", String::class),
      arguments("complex.string", String::class),
      arguments("complex.boolean", Boolean::class),
      arguments("complex.integer", Integer::class),
      arguments("complex.localDate", LocalDate::class),
      arguments("complex.localDateTime", LocalDateTime::class),
      arguments("complex.nested.code", String::class),
      arguments("complex.nested.description", String::class),
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
