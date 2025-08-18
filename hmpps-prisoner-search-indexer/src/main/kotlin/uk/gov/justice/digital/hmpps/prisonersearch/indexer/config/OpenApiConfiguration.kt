package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://prisoner-search-indexer-dev.prison.service.justice.gov.uk/").description("Development"),
        Server().url("https://prisoner-search-indexer-preprod.prison.service.justice.gov.uk/").description("Pre-Production"),
        Server().url("https://prisoner-search-indexer.prison.service.justice.gov.uk/").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .info(
      Info().title("HMPPS Prisoner Search Indexer")
        .version(version)
        .description(javaClass.getResource("/swagger-description.html")!!.readText())
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .components(
      Components().addSecuritySchemes(
        "prisoner-index-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_PRISONER_INDEX"),
      ),
    )
    .addSecurityItem(SecurityRequirement().addList("prisoner-index-role", listOf("read", "write")))
}

private fun SecurityScheme.addBearerJwtRequirement(role: String): SecurityScheme = type(SecurityScheme.Type.HTTP)
  .scheme("bearer")
  .bearerFormat("JWT")
  .`in`(SecurityScheme.In.HEADER)
  .name("Authorization")
  .description("A HMPPS Auth access token with the `$role` role.")
