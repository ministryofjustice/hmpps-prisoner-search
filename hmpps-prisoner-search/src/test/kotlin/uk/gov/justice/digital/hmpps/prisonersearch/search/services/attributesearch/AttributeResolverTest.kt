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
      arguments(
        "string", String::class,
        "boolean", Boolean::class,
        "integer", Integer::class,
        "localDate", LocalDate::class,
        "localDateTime", LocalDateTime::class,
        "list.string", String::class,
        "list.boolean", Boolean::class,
        "list.integer", Integer::class,
        "list.localDate", LocalDate::class,
        "list.localDateTime", LocalDateTime::class,
        "list.nested.code", String::class,
        "list.nested.description", String::class,
        "complex.string", String::class,
        "complex.boolean", Boolean::class,
        "complex.integer", Integer::class,
        "complex.localDate", LocalDate::class,
        "complex.localDateTime", LocalDateTime::class,
        "complex.nested.code", String::class,
        "complex.nested.description", String::class,
      ),
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
