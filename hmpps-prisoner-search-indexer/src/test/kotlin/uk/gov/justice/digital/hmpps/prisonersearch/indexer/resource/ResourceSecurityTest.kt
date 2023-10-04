package uk.gov.justice.digital.hmpps.prisonersearch.indexer.resource

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import java.io.File

class ResourceSecurityTest : IntegrationTestBase() {
  @Autowired
  private lateinit var context: ApplicationContext

  private val unprotectedDefaultMethods = setOf(
    "/v3/api-docs.yaml",
    "/swagger-ui.html",
    "/v3/api-docs",
    "/v3/api-docs/swagger-config",
    "/error",
  )

  @Test
  fun `Ensure all endpoints protected with PreAuthorize`() {
    // need to exclude any that are forbidden in helm configuration
    val exclusions = File("helm_deploy").walk().filter { it.name.equals("values.yaml") }.flatMap { file ->
      file.readLines().map { line ->
        line.takeIf { it.contains("location") }?.substringAfter("location ")?.substringBefore(" {")
      }
    }.filterNotNull().toMutableSet().also {
      it.addAll(unprotectedDefaultMethods)
    }

    context.getBeansOfType(RequestMappingHandlerMapping::class.java).forEach { (_, mapping) ->
      mapping.handlerMethods.forEach { (mappingInfo, method) ->
        val annotation = method.getMethodAnnotation(PreAuthorize::class.java)
        if (annotation == null) {
          mappingInfo.directPaths.forEach { path ->
            assertThat(exclusions.contains(path)).withFailMessage {
              "Found $mappingInfo of type $method with no PreAuthorize annotation"
            }.isTrue()
          }
        }
      }
    }
  }
}
