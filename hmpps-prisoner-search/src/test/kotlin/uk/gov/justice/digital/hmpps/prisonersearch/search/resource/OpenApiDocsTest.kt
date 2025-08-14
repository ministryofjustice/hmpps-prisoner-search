package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.SimpleRequest
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.search.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
      .expectBody()
      .jsonPath("paths").isNotEmpty
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
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty
  }

  @Test
  fun `atlassian request validator reports no validation errors on valid attribute search request`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)

    // needed so that swagger schema uses the types array instead - see https://github.com/swagger-api/swagger-parser/issues/1821
    System.setProperty("bind-type", "true")
    val validator = OpenApiInteractionValidator.createFor(result.openAPI).build()
    System.setProperty("bind-type", "false")

    val report = validator.validateRequest(
      SimpleRequest.Builder
        .post("/attribute-search")
        // needed so that the request json is validated
        .withAccept(MediaType.APPLICATION_JSON_VALUE)
        .withContentType(MediaType.APPLICATION_JSON_VALUE)
        .withBody(
          """
            {
              "joinType": "AND",
              "queries": [
                {
                  "joinType": "AND",
                  "matchers": [
                    {
                      "type": "String",
                      "attribute": "prisonId",
                      "condition": "IS",
                      "searchTerm": "MDI"
                    },
                    {
                      "type": "String",
                      "attribute": "cellLocation",
                      "condition": "IS",
                      "searchTerm": "A-1-002"
                    }
                  ]
                }
              ],
              "pagination": {
                "page": 0,
                "size": 10
              }
            }
          """.trimIndent(),
        )
        .withAuthorization("Bearer 12345")
        .build(),
    )
    assertThat(report.messages).isEmpty()
    assertThat(report.hasErrors()).isFalse
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_VIEW_PRISONER_DATA role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.view-prisoner-data-role.description").value(containsString("ROLE_VIEW_PRISONER_DATA"))
      .jsonPath("$.security[0].view-prisoner-data-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-search-role.description").value(containsString("ROLE_PRISONER_SEARCH"))
      .jsonPath("$.security[1].prisoner-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_GLOBAL_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.global-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.global-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.global-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.global-search-role.description").value(containsString("ROLE_GLOBAL_SEARCH"))
      .jsonPath("$.security[2].global-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the ROLE_PRISONER_IN_PRISON_SEARCH role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-in-prison-search-role.description").value(containsString("ROLE_PRISONER_IN_PRISON_SEARCH"))
      .jsonPath("$.security[3].prisoner-in-prison-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }

  @Test
  fun `a security scheme is setup for a HMPPS Auth token with the PRISONER_SEARCH__PRISONER__RO role`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.components.securitySchemes.prisoner-search--prisoner--ro.description").value(containsString("PRISONER_SEARCH__PRISONER__RO"))
      .jsonPath("$.security[3].prisoner-in-prison-search-role")
      .isEqualTo(JSONArray().apply { addAll(listOf("read")) })
  }
}
