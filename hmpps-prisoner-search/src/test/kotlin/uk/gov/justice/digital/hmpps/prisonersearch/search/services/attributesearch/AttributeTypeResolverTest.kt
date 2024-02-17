package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import getAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Stream

class AttributeTypeResolverTest {
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
      of("string", AttributeType.STRING),
      of("boolean", AttributeType.BOOLEAN),
      of("integer", AttributeType.INTEGER),
      of("localDate", AttributeType.DATE),
      of("localDateTime", AttributeType.DATE_TIME),
      of("list.string", AttributeType.STRING),
      of("list.boolean", AttributeType.BOOLEAN),
      of("list.integer", AttributeType.INTEGER),
      of("list.localDate", AttributeType.DATE),
      of("list.localDateTime", AttributeType.DATE_TIME),
      of("list.nested.code", AttributeType.STRING),
      of("list.nested.description", AttributeType.STRING),
      of("complex.string", AttributeType.STRING),
      of("complex.boolean", AttributeType.BOOLEAN),
      of("complex.integer", AttributeType.INTEGER),
      of("complex.localDate", AttributeType.DATE),
      of("complex.localDateTime", AttributeType.DATE_TIME),
      of("complex.nested.code", AttributeType.STRING),
      of("complex.nested.description", AttributeType.STRING),
    )
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  fun `should resolve attributes`(attributeName: String, type: AttributeType) {
    assertThat(attributes[attributeName]).isEqualTo(type)
  }

  @Test
  fun `should not resolve extra attributes`() {
    assertThat(attributes.size).isEqualTo(19)
  }
}
