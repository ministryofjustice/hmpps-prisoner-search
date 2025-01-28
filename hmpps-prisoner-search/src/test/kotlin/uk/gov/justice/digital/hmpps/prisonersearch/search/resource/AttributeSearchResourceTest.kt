package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Attribute
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * This class tests the attribute search API using raw JSON using a rudimentary JSON request builder. This allows us to test that invalid JSON is rejected.
 *
 * For clearer examples of JSON requests see [AttributeSearchRequestJsonTest].
 */
class AttributeSearchResourceTest : AbstractSearchDataIntegrationTest() {
  private fun validRequest(
    matchers: String = """
      {
        "type": "String",
        "attribute": "lastName",
        "condition": "IS",
        "searchTerm": "Smith"
      }
    """.trimIndent(),
  ) = """
      {
         "queries": [
          {
            "joinType": "AND",
            "matchers": [$matchers]
          }
        ]
      }
  """.trimIndent()

  private val today = LocalDate.now()
  private val now = LocalDateTime.now()

  @Nested
  inner class Security {
    @Test
    fun `should be unauthorized without valid token`() {
      webTestClient.post()
        .uri("/attribute-search")
        .header("Content-Type", "application/json")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should be forbidden without a role`() {
      webTestClient.post()
        .uri("/attribute-search")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation())
        .bodyValue(validRequest())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should be forbidden with wrong role`() {
      webTestClient.post()
        .uri("/attribute-search")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_UNKNOWN")))
        .bodyValue(validRequest())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should be OK with PRISONER_SEARCH role`() {
      webTestClient.post()
        .uri("/attribute-search")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
        .bodyValue(validRequest())
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `should be OK with GLOBAL_SEARCH role`() {
      webTestClient.post()
        .uri("/attribute-search")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .bodyValue(validRequest())
        .exchange()
        .expectStatus().isOk
    }
  }

  /**
   * This class is to document the API behaviour and provide regression tests once that behaviour is settled.
   *
   * We need to document this behaviour because some of it isn't obvious - e.g. it's OK to pass an Int where a Boolean is expected. Also changing the JSON parser or configuring Jackson differently could change this behaviour. Which might be fine but it's good to know!
   */
  @Nested
  inner class API {
    @Nested
    inner class StringValidation {

      private fun validStringRequest() = validRequest(validStringMatcher())

      private fun validStringMatcher() = """
      {
        "type": "String",
        "attribute": "lastName",
        "condition": "IS",
        "searchTerm": "Smith"
      }
      """.trimIndent()

      private fun String.withJoinType(joinType: String) = replace(""""joinType": "AND"""", """"joinType": "$joinType"""")

      private fun String.withType(type: String) = replace(""""type": "String"""", """"type": "$type"""")
      private fun String.withAttribute(attribute: String) = replace(""""attribute": "lastName"""", """"attribute": "$attribute"""")

      private fun String.withCondition(condition: String) = replace(""""condition": "IS"""", """"condition": "$condition"""")

      private fun String.withSearchTermJson(searchTermJson: String) = replace(""""searchTerm": "Smith"""", searchTermJson)

      @Test
      fun `should return OK for a valid request`() {
        webTestClient.attributeSearch(validStringRequest())
          .expectStatus().isOk
      }

      @Test
      fun `should return bad request for wrong join type`() {
        webTestClient.attributeSearch(validStringRequest().withJoinType("UNKNOWN"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("UNKNOWN")
          }
      }

      @Test
      fun `should return bad request for unknown matcher type`() {
        webTestClient.attributeSearch(validStringRequest().withType("Unknown"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Unknown")
          }
      }

      @Test
      fun `should return bad request for wrong matcher type`() {
        webTestClient.attributeSearch(validStringRequest().withType("Boolean"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("boolean")
          }
      }

      @Test
      fun `should return bad request for wrong attribute type`() {
        webTestClient.attributeSearch(validStringRequest().withAttribute("releaseDate"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("releaseDate").contains("LocalDate").contains("String")
          }
      }

      @Test
      fun `should return bad request for unknown condition`() {
        webTestClient.attributeSearch(validStringRequest().withCondition("Unknown"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Unknown")
          }
      }

      @Test
      fun `should return bad request if passing an invalid search term`() {
        webTestClient.attributeSearch(validStringRequest().withSearchTermJson(""""searchTerm": unknown"""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("unknown")
          }
      }

      // It's OK to pass a Boolean instead of a String
      @Test
      fun `should return OK if passing a Boolean as the search term`() {
        webTestClient.attributeSearch(validStringRequest().withSearchTermJson(""""searchTerm": true"""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as StringMatcher).searchTerm).isEqualTo("true")
          },
          any(),
        )
      }

      // It's OK to pass a Date instead of a String
      @Test
      fun `should return OK if passing a Date as the search term`() {
        webTestClient.attributeSearch(validStringRequest().withSearchTermJson(""""searchTerm": "$today""""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as StringMatcher).searchTerm).isEqualTo("$today")
          },
          any(),
        )
      }

      // It's OK to pass a DateTime instead of a String
      @Test
      fun `should return OK if passing a DateTime as the search term`() {
        webTestClient.attributeSearch(validStringRequest().withSearchTermJson(""""searchTerm": "$now""""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as StringMatcher).searchTerm).isEqualTo("$now")
          },
          any(),
        )
      }

      // It's OK to pass an Int instead of a String
      @Test
      fun `should return OK if passing an Int as the search term`() {
        webTestClient.attributeSearch(validStringRequest().withSearchTermJson(""""searchTerm": 1234"""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as StringMatcher).searchTerm).isEqualTo("1234")
          },
          any(),
        )
      }
    }

    @Nested
    inner class BooleanValidation {

      private fun validBooleanRequest() = validRequest(validBooleanMatcher())

      private fun validBooleanMatcher() = """
      {
        "type": "Boolean",
        "attribute": "active",
        "condition": true
      }
      """.trimIndent()

      private fun String.withType(type: String) = replace(""""type": "Boolean"""", """"type": "$type"""")
      private fun String.withAttribute(attribute: String) = replace(""""attribute": "active"""", """"attribute": "$attribute"""")

      private fun String.withConditionJson(conditionJson: String) = replace(""""condition": true""", conditionJson)

      @Test
      fun `should return OK for a valid request`() {
        webTestClient.attributeSearch(validBooleanRequest())
          .expectStatus().isOk
      }

      @Test
      fun `should return bad request for wrong matcher type`() {
        webTestClient.attributeSearch(validBooleanRequest().withType("Date"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("active").contains("Boolean").contains("LocalDate")
          }
      }

      @Test
      fun `should return bad request for wrong attribute type`() {
        webTestClient.attributeSearch(validBooleanRequest().withAttribute("firstName"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("firstName").contains("Boolean").contains("String")
          }
      }

      // It's OK to pass a Boolean in String format
      @Test
      fun `should return OK when passing a boolean in String format as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": "true""""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as BooleanMatcher).condition).isTrue()
          },
          any(),
        )
      }

      @Test
      fun `should return bad request when passing a Date as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": "$today""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("boolean")
          }
      }

      @Test
      fun `should return bad request when passing a DateTime as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": "$now""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("boolean")
          }
      }

      // It's OK to pass an Int instead of a Boolean
      @Test
      fun `should return OK and parse as true when passing an Int as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": 123"""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as BooleanMatcher).condition).isTrue()
          },
          any(),
        )
      }

      // It's OK to pass a zero Int instead of a Boolean
      @Test
      fun `should return OK and parse as false when passing zero as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": 0"""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as BooleanMatcher).condition).isFalse()
          },
          any(),
        )
      }

      @Test
      fun `should return bad request when passing a String as the condition`() {
        webTestClient.attributeSearch(validBooleanRequest().withConditionJson(""""condition": "John""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("boolean")
          }
      }
    }

    @Nested
    inner class DateValidation {
      private fun validDateRequest() = validRequest(validDateMatcher())

      private fun validDateMatcher() = """
      {
        "type": "Date",
        "attribute": "releaseDate",
        "minValue": "2023-01-01",
        "maxValue": "2024-01-01"
      }
      """.trimIndent()

      private fun String.withType(type: String) = replace(""""type": "Date"""", """"type": "$type"""")
      private fun String.withAttribute(attribute: String) = replace(""""attribute": "releaseDate"""", """"attribute": "$attribute"""")

      private fun String.withMinValueJson(minValueJson: String) = replace(""""minValue": "2023-01-01"""", minValueJson)

      private fun String.withMaxValueJson(maxValueJson: String) = replace(""""maxValue": "2024-01-01"""", maxValueJson)

      @Test
      fun `should return OK for a valid request`() {
        webTestClient.attributeSearch(validDateRequest())
          .expectStatus().isOk
      }

      @Test
      fun `should return bad request for wrong matcher type`() {
        webTestClient.attributeSearch(validDateRequest().withType("String"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("String")
          }
      }

      @Test
      fun `should return bad request for wrong attribute type`() {
        webTestClient.attributeSearch(validDateRequest().withAttribute("heightCentimetres"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("heightCentimetres").contains("Int").contains("LocalDate")
          }
      }

      @Test
      fun `should return bad request when passing Boolean as date value`() {
        webTestClient.attributeSearch(validDateRequest().withMinValueJson(""""minValue": true"""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error")
          }
      }

      // It's OK to pass an Int instead of a Date
      @Test
      fun `should return OK when passing Int as date value`() {
        webTestClient.attributeSearch(validDateRequest().withMinValueJson(""""minValue": 1"""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as DateMatcher).minValue?.year).isEqualTo(1970)
          },
          any(),
        )
      }

      // It's OK to pass a DateTime instead of a Date
      @Test
      fun `should return OK when passing DateTime as min value`() {
        webTestClient.attributeSearch(validDateRequest().withMaxValueJson(""""maxValue": "$now""""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as DateMatcher).maxValue).isEqualTo(now.toLocalDate())
          },
          any(),
        )
      }

      @Test
      fun `should return bad request when passing String as min value`() {
        webTestClient.attributeSearch(validDateRequest().withMinValueJson(""""minValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("LocalDate")
          }
      }

      @Test
      fun `should return bad request for invalid max value`() {
        webTestClient.attributeSearch(validDateRequest().withMaxValueJson(""""maxValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("LocalDate")
          }
      }
    }

    @Nested
    inner class DateTimeValidation {
      private fun validDateTimeRequest() = validRequest(validDateTimeMatcher())

      private fun validDateTimeMatcher() = """
      {
        "type": "DateTime",
        "attribute": "currentIncentive.dateTime",
        "minValue": "2023-01-01T09:14:33",
        "maxValue": "2023-02-28T21:41:44"
      }
      """.trimIndent()

      private fun String.withType(type: String) = replace(""""type": "DateTime"""", """"type": "$type"""")
      private fun String.withAttribute(attribute: String) = replace(""""attribute": "currentIncentive.dateTime"""", """"attribute": "$attribute"""")

      private fun String.withMinValueJson(minValueJson: String) = replace(""""minValue": "2023-01-01T09:14:33"""", minValueJson)

      private fun String.withMaxValueJson(maxValueJson: String) = replace(""""maxValue": "2023-02-28T21:41:44"""", maxValueJson)

      @Test
      fun `should return OK for a valid request`() {
        webTestClient.attributeSearch(validDateTimeRequest())
          .expectStatus().isOk
      }

      @Test
      fun `should return bad request for wrong matcher type`() {
        webTestClient.attributeSearch(validDateTimeRequest().withType("String"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("String")
          }
      }

      @Test
      fun `should return bad request for wrong attribute type`() {
        webTestClient.attributeSearch(validDateTimeRequest().withAttribute("heightCentimetres"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("heightCentimetres").contains("Int").contains("LocalDateTime")
          }
      }

      @Test
      fun `should return bad request when passing Boolean as date time value`() {
        webTestClient.attributeSearch(validDateTimeRequest().withMinValueJson(""""minValue": true"""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error")
          }
      }

      @Test
      fun `should return bad request when passing Int as date time value`() {
        webTestClient.attributeSearch(validDateTimeRequest().withMinValueJson(""""minValue": 1"""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error")
          }
      }

      @Test
      fun `should return bad request when passing DateTime as date time value`() {
        webTestClient.attributeSearch(validDateTimeRequest().withMaxValueJson(""""maxValue": "$today""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("LocalDateTime")
          }
      }

      @Test
      fun `should return bad request when passing String as min value`() {
        webTestClient.attributeSearch(validDateTimeRequest().withMinValueJson(""""minValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("LocalDateTime")
          }
      }

      @Test
      fun `should return bad request for invalid max value`() {
        webTestClient.attributeSearch(validDateTimeRequest().withMaxValueJson(""""maxValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("LocalDateTime")
          }
      }
    }

    @Nested
    inner class IntValidation {
      private fun validIntRequest() = validRequest(validIntMatcher())

      private fun validIntMatcher() = """
      {
        "type": "Int",
        "attribute": "heightCentimetres",
        "minValue": 150,
        "maxValue": 160
      }
      """.trimIndent()

      private fun String.withType(type: String) = replace(""""type": "Int"""", """"type": "$type"""")
      private fun String.withAttribute(attribute: String) = replace(""""attribute": "heightCentimetres"""", """"attribute": "$attribute"""")

      private fun String.withMinValueJson(minValueJson: String) = replace(""""minValue": 150""", minValueJson)

      private fun String.withMaxValueJson(maxValueJson: String) = replace(""""maxValue": 160""", maxValueJson)

      @Test
      fun `should return OK for a valid request`() {
        webTestClient.attributeSearch(validIntRequest())
          .expectStatus().isOk
      }

      @Test
      fun `should return bad request for wrong matcher type`() {
        webTestClient.attributeSearch(validIntRequest().withType("String"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("String")
          }
      }

      @Test
      fun `should return bad request for wrong attribute type`() {
        webTestClient.attributeSearch(validIntRequest().withAttribute("releaseDate"))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("release").contains("Int").contains("LocalDate")
          }
      }

      @Test
      fun `should return bad request when passing Boolean as int value`() {
        webTestClient.attributeSearch(validIntRequest().withMinValueJson(""""minValue": true"""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Int")
          }
      }

      @Test
      fun `should return bad request when passing date as min value`() {
        webTestClient.attributeSearch(validIntRequest().withMinValueJson(""""minValue": "$today""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Int")
          }
      }

      @Test
      fun `should return bad request when passing date as max value`() {
        webTestClient.attributeSearch(validIntRequest().withMaxValueJson(""""maxValue": "$today""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Int")
          }
      }

      // It's OK to wrap an Int in a String
      @Test
      fun `should return OK when passing an Int as a String`() {
        webTestClient.attributeSearch(validIntRequest().withMinValueJson(""""minValue": "140""""))
          .expectStatus().isOk

        verify(attributeSearchService).search(
          check {
            assertThat((it.queries.first().matchers?.first() as IntMatcher).minValue).isEqualTo(140)
          },
          any(),
        )
      }

      @Test
      fun `should return bad request when passing String as min value`() {
        webTestClient.attributeSearch(validIntRequest().withMinValueJson(""""minValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Int")
          }
      }

      @Test
      fun `should return bad request when passing String max value`() {
        webTestClient.attributeSearch(validIntRequest().withMaxValueJson(""""maxValue": "black""""))
          .expectStatus().isBadRequest
          .expectBody().jsonPath("$.userMessage").value<String> {
            assertThat(it).contains("JSON parse error").contains("Int")
          }
      }
    }
  }

  @Nested
  inner class Telemetry {
    @Test
    fun `should track query`() {
      webTestClient.attributeSearch(
        """
        {
          "queries": [
            {
              "joinType": "AND",
              "matchers": [
                {
                  "type": "String",
                  "attribute": "firstName",
                  "condition": "IS",
                  "searchTerm": "John"
                }
              ]
            }
          ]
        }
        """.trimIndent(),
      )
        .expectStatus().isOk

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("firstName = John")
          assertThat(it["pageable"]).isEqualTo("Page request [number: 0, size 10, sort: UNSORTED]")
          assertThat(it["resultCount"]?.toInt()).isNotNull()
          assertThat(it["totalHits"]?.toInt()).isNotNull()
          assertThat(it["timeInMs"]?.toInt()).isNotNull()
        },
        isNull(),
      )
    }

    @Test
    fun `should track invalid query`() {
      webTestClient.attributeSearch(
        """
        {
          "queries": [
            {
              "matchers": [
                {
                  "type": "String",
                  "attribute": "heightCentimetres",
                  "condition": "IS",
                  "searchTerm": "John"
                }
              ]
            }
          ]
        }
        """.trimIndent(),
      )
        .expectStatus().isBadRequest

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearchError"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres = John")
          assertThat(it["pageable"]).isEqualTo("Page request [number: 0, size 10, sort: UNSORTED]")
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class GetAttributes {
    @Test
    fun `should be unauthorized without valid token`() {
      webTestClient.get()
        .uri("/attribute-search/attributes")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should be forbidden without a role`() {
      webTestClient.get()
        .uri("/attribute-search/attributes")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should be forbidden with wrong role`() {
      webTestClient.get()
        .uri("/attribute-search/attributes")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_UNKNOWN")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return attributes`() {
      webTestClient.get()
        .uri("/attribute-search/attributes")
        .header("Content-Type", "application/json")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<List<Attribute>> {
          assertThat(it).extracting("name", "type", "fuzzySearch").contains(
            tuple("firstName", "String", true),
            tuple("recall", "Boolean", false),
            tuple("heightCentimetres", "Int", false),
            tuple("releaseDate", "Date", false),
            tuple("currentIncentive.dateTime", "DateTime", false),
            tuple("currentIncentive.level.code", "String", false),
            tuple("aliases.firstName", "String", true),
          )
        }
    }
  }

  private fun WebTestClient.attributeSearch(body: String) = post()
    .uri("/attribute-search")
    .header("Content-Type", "application/json")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
    .bodyValue(body)
    .exchange()
}
