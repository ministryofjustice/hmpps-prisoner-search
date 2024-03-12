package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Stream

class AttributeResolverTest {
  val attributes = getAttributes(Root::class)

  private class Root(
    @Field(type = FieldType.Keyword)
    val keywordString: String,
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
    @Field(type = FieldType.Keyword)
    val code: String,
    val description: String,
  )

  companion object {
    @JvmStatic
    fun testParameters(): Stream<Arguments> = Stream.of(
      arguments("keywordString", Attribute(String::class, "keywordString")),
      arguments("string", Attribute(String::class, "string.keyword")),
      arguments("boolean", Attribute(Boolean::class, "boolean")),
      arguments("integer", Attribute(Integer::class, "integer")),
      arguments("localDate", Attribute(LocalDate::class, "localDate")),
      arguments("localDateTime", Attribute(LocalDateTime::class, "localDateTime")),
      arguments("list.string", Attribute(String::class, "list.string.keyword")),
      arguments("list.boolean", Attribute(Boolean::class, "list.boolean")),
      arguments("list.integer", Attribute(Integer::class, "list.integer")),
      arguments("list.localDate", Attribute(LocalDate::class, "list.localDate")),
      arguments("list.localDateTime", Attribute(LocalDateTime::class, "list.localDateTime")),
      arguments("list.nested.code", Attribute(String::class, "list.nested.code")),
      arguments("list.nested.description", Attribute(String::class, "list.nested.description.keyword")),
      arguments("complex.string", Attribute(String::class, "complex.string.keyword")),
      arguments("complex.boolean", Attribute(Boolean::class, "complex.boolean")),
      arguments("complex.integer", Attribute(Integer::class, "complex.integer")),
      arguments("complex.localDate", Attribute(LocalDate::class, "complex.localDate")),
      arguments("complex.localDateTime", Attribute(LocalDateTime::class, "complex.localDateTime")),
      arguments("complex.nested.code", Attribute(String::class, "complex.nested.code")),
      arguments("complex.nested.description", Attribute(String::class, "complex.nested.description.keyword")),
    )
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  fun `should resolve attributes`(attributeName: String, attribute: Attribute) {
    assertThat(attributes[attributeName]?.type).isEqualTo(attribute.type)
    assertThat(attributes[attributeName]?.openSearchName).isEqualTo(attribute.openSearchName)
  }

  @Test
  fun `should not resolve extra attributes`() {
    assertThat(attributes.size).isEqualTo(20)
  }
}
