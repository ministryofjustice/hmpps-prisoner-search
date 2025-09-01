package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException

class ResponseFieldsMapperTest {
  private val mapper = ResponseFieldsMapper()

  @Nested
  inner class PassthroughWhenClientBlankOrNull {
    @Test
    fun `returns same list when client is null`() {
      val input = listOf("a", "b")
      val result = mapper.translate(input, null)
      assertThat(result).isEqualTo(input)
    }

    @Test
    fun `returns same list when client is blank`() {
      val input = listOf("x")
      val result = mapper.translate(input, " ")
      assertThat(result).isEqualTo(input)
    }

    @Test
    fun `returns null when client is null and responseFields is null`() {
      val result = mapper.translate(null, null)
      assertThat(result).isNull()
    }
  }

  @Nested
  inner class KnownClientRestrictedPatients {
    private val client = "restricted-patients"

    @Test
    fun `returns defaults when responseFields is null`() {
      val result = mapper.translate(null, client)
      assertThat(result).isNotNull
      assertThat(result).containsExactlyElementsOf(ResponseFieldsMapper.clientMap.getValue(client))
    }

    @Test
    fun `returns union of defaults and extras without duplicates, keeping encounter order`() {
      val extras = listOf("extra1", "firstName", "extra2", "extra1")
      val result = mapper.translate(extras, client)

      // Expected order: all defaults in order, then extras in encounter order excluding ones already present and duplicates
      val expected = ResponseFieldsMapper.clientMap.getValue(client).toMutableList() + "extra1" + "extra2"

      assertThat(result).containsExactlyElementsOf(expected)
    }
  }

  @Nested
  inner class UnknownClient {
    @Test
    fun `throws exception for unknown client`() {
      assertThatThrownBy { mapper.translate(null, "some-client") }
        .isInstanceOf(BadRequestException::class.java).hasMessage("Invalid response fields client requested: some-client")
    }
  }
}
