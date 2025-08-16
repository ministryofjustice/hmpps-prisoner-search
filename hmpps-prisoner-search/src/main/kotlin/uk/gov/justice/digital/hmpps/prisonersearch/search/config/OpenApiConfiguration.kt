package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
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
        Server().url("https://prisoner-search-dev.prison.service.justice.gov.uk/").description("Development"),
        Server().url("https://prisoner-search-preprod.prison.service.justice.gov.uk/").description("Pre-Production"),
        Server().url("https://prisoner-search.prison.service.justice.gov.uk/").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .tags(
      listOf(
        Tag().name("Popular")
          .description("The most popular endpoints. Look here first when deciding which endpoint to use."),
        Tag().name("Establishment search").description("Endpoints for searching for a prisoner within a prison"),
        Tag().name("Global search")
          .description("Endpoints for searching for a prisoner across the entire prison estate, including people that have previously been released"),
        Tag().name("Batch").description("Endpoints designed to find a large number of prisoners with a single call"),
        Tag().name("Matching").description("Endpoints designed for matching a prisoner with data from other sources"),
        Tag().name("Deprecated")
          .description("Endpoints that should no longer be used and will be removed in a future release"),
        Tag().name("Specific use case")
          .description("Endpoints that were designed for a specific use case and are unlikely to fit for general use"),
        Tag().name("Experimental")
          .description("Endpoints that have not been tried and tested in a production environment"),
      ),
    )
    .info(
      Info().title("Prisoner Search").version(version)
        .description(this.javaClass.getResource("/documentation/service-description.html")!!.readText())
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .components(
      Components().addSecuritySchemes(
        "view-prisoner-data-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_VIEW_PRISONER_DATA"),
      ).addSecuritySchemes(
        "prisoner-search-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_PRISONER_SEARCH"),
      ).addSecuritySchemes(
        "global-search-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_GLOBAL_SEARCH"),
      ).addSecuritySchemes(
        "prisoner-in-prison-search-role",
        SecurityScheme().addBearerJwtRequirement("ROLE_PRISONER_IN_PRISON_SEARCH"),
      ).addSecuritySchemes(
        "prisoner-search--prisoner--ro",
        SecurityScheme().addBearerJwtRequirement("PRISONER_SEARCH__PRISONER__RO"),
      ),
    )
    .addSecurityItem(SecurityRequirement().addList("view-prisoner-data-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("global-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-in-prison-search-role", listOf("read")))
    .addSecurityItem(SecurityRequirement().addList("prisoner-search--prisoner--ro", listOf("read")))

//  @Bean
//  fun myCustomModelResolver(objectMapper: ObjectMapper?): ModelResolver = object : ModelResolver(objectMapper) {
//    override fun resolveDiscriminator(
//      type: JavaType?,
//      context: ModelConverterContext?,
//    ): Discriminator? {
//      val discriminator = super.resolveDiscriminator(type, context)
//      if (context != null &&
//        type != null &&
//        discriminator != null &&
//        discriminator.propertyName != null &&
//        (discriminator.mapping == null || discriminator.mapping.isEmpty()) // don't override anything
//      ) {
//        val jsonSubTypes = type.rawClass.getDeclaredAnnotation(JsonSubTypes::class.java)
//        jsonSubTypes?.value?.forEach { subtype: JsonSubTypes.Type ->
//          val ref = RefUtils.constructRef(context.resolve(AnnotatedType(subtype.value.java)).name)
//          discriminator.mapping(subtype.name, ref) // add mapping
//        }
//      }
//      return discriminator
//    }
//  }
}

private fun SecurityScheme.addBearerJwtRequirement(role: String): SecurityScheme = type(SecurityScheme.Type.HTTP)
  .scheme("bearer")
  .bearerFormat("JWT")
  .`in`(SecurityScheme.In.HEADER)
  .name("Authorization")
  .description("A HMPPS Auth access token with the `$role` role.")
