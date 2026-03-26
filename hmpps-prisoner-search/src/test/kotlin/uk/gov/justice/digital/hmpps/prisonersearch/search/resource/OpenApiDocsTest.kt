package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.text.contains
import kotlin.text.get

@AutoConfigureWebTestClient(timeout = "PT60S")
class OpenApiDocsTest : IntegrationTestBase() {
  @LocalServerPort
  private val port: Int = 0

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("paths").isNotEmpty
  }

  @Test
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty
  }

  @Test
  fun `the open api json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").value<String> {
        assertThat(it).startsWith(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
      }
  }

  @Test
  fun `the generated open api for date times hasn't got the time zone`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.Identifier.properties.createdDateTime.example").isEqualTo("2020-07-17T12:34:56.833Z")
      .jsonPath("$.components.schemas.Identifier.properties.createdDateTime.description")
      .isEqualTo("The date/time the identifier was created in the system. Will never be null.")
      .jsonPath("$.components.schemas.Identifier.properties.createdDateTime.type").isEqualTo("string")
      .jsonPath("$.components.schemas.Identifier.properties.createdDateTime.format").isEqualTo("date-time")
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "0, view-prisoner-data-role, ROLE_VIEW_PRISONER_DATA",
      "1, prisoner-search-role, ROLE_PRISONER_SEARCH",
      "2, global-search-role, ROLE_GLOBAL_SEARCH",
      "3, prisoner-in-prison-search-role, ROLE_PRISONER_IN_PRISON_SEARCH",
      "4, prisoner-search--prisoner--ro, PRISONER_SEARCH__PRISONER__RO",
    ],
  )
  fun `the security scheme is setup for bearer tokens`(index: Int, key: String, role: String) {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.$key.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.$key.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.$key.description").value<String> {
        assertThat(it).contains(role)
      }
      .jsonPath("$.components.securitySchemes.$key.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.security[$index].$key").isEqualTo(JSONArray().apply { add("read") })
  }

  @Test
  fun `the open api json doesn't include LocalTime`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("components.schemas.LocalTime").doesNotExist()
  }

  @Test
  fun `the response contains required fields`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.ErrorResponse.required").value<List<String>> {
        assertThat(it).containsExactlyInAnyOrder("status", "errorCode", "userMessage")
      }
  }

  @Test
  fun `the swagger json don't contain any duplicate methods`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("*..operationId").value<List<String>> { list ->
        assertThat(list).filteredOn { it.contains("_") }.isEmpty()
      }
  }
}
