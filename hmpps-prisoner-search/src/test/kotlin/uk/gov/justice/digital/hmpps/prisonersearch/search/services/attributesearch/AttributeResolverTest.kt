package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
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
    @Field(type = FieldType.Nested)
    val list: List<Complex>,
    @Field(type = FieldType.Nested)
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

  private class ListSimpleType(
    val listString: List<String>,
  )

  companion object {
    @JvmStatic
    fun testParameters(): Stream<Arguments> = Stream.of(
      arguments("keywordString", Attribute(String::class, "keywordString", false)),
      arguments("string", Attribute(String::class, "string.keyword", false)),
      arguments("boolean", Attribute(Boolean::class, "boolean", false)),
      arguments("integer", Attribute(Integer::class, "integer", false)),
      arguments("localDate", Attribute(LocalDate::class, "localDate", false)),
      arguments("localDateTime", Attribute(LocalDateTime::class, "localDateTime", false)),
      arguments("list.string", Attribute(String::class, "list.string.keyword", true)),
      arguments("list.boolean", Attribute(Boolean::class, "list.boolean", true)),
      arguments("list.integer", Attribute(Integer::class, "list.integer", true)),
      arguments("list.localDate", Attribute(LocalDate::class, "list.localDate", true)),
      arguments("list.localDateTime", Attribute(LocalDateTime::class, "list.localDateTime", true)),
      arguments("list.nested.code", Attribute(String::class, "list.nested.code", true)),
      arguments("list.nested.description", Attribute(String::class, "list.nested.description.keyword", true)),
      arguments("complex.string", Attribute(String::class, "complex.string.keyword", true)),
      arguments("complex.boolean", Attribute(Boolean::class, "complex.boolean", true)),
      arguments("complex.integer", Attribute(Integer::class, "complex.integer", true)),
      arguments("complex.localDate", Attribute(LocalDate::class, "complex.localDate", true)),
      arguments("complex.localDateTime", Attribute(LocalDateTime::class, "complex.localDateTime", true)),
      arguments("complex.nested.code", Attribute(String::class, "complex.nested.code", true)),
      arguments("complex.nested.description", Attribute(String::class, "complex.nested.description.keyword", true)),
    )
  }

  @ParameterizedTest
  @MethodSource("testParameters")
  fun `should resolve attributes`(attributeName: String, attribute: Attribute) {
    assertThat(attributes[attributeName]?.type).isEqualTo(attribute.type)
    assertThat(attributes[attributeName]?.openSearchName).isEqualTo(attribute.openSearchName)
    assertThat(attributes[attributeName]?.isNested).isEqualTo(attribute.isNested)
  }

  @Test
  fun `should not resolve extra attributes`() {
    assertThat(attributes.size).isEqualTo(20)
  }

  @Test
  fun `should resolve reference data attributes`() {
    val prisonerAttributes = getAttributes(Prisoner::class)

    assertThat(prisonerAttributes["firstName"]?.isFuzzy).isTrue()
    assertThat(prisonerAttributes["build"]?.isFuzzy).isFalse()
    assertThat(prisonerAttributes["marks.comment"]?.isFuzzy).isTrue()
    assertThat(prisonerAttributes["marks.bodyPart"]?.isFuzzy).isFalse()
    assertThat(prisonerAttributes["aliases.firstName"]?.isFuzzy).isTrue()
  }

  @Test
  fun `should reject lists of simple types`() {
    assertThrows<InvalidAttributeTypeException> {
      getAttributes(ListSimpleType::class)
    }.also {
      assertThat(it.message).contains("listString").contains("Lists of simple types are not supported")
    }
  }
}
