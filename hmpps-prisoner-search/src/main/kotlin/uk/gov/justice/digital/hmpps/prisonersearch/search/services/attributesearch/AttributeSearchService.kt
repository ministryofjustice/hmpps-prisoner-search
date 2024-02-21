package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

@Component
class AttributeSearchService(
  private val attributes: Attributes,
  private val telemetryClient: TelemetryClient,
) {

  fun search(request: AttributeSearchRequest, pageable: Pageable = Pageable.unpaged()): Page<Prisoner> {
    telemetryClient.trackEvent("POSAttributeSearch", mapOf("query" to request.query.toString()), null)
    request.validate(attributes)
    log.info("searchByAttributes called with request: $request, pageable: $pageable")
    return Page.empty()
  }

  fun getAttributes() = attributes.map { it.key to it.value.toString().lastWord() }.toMap()

  private fun String.lastWord() = split(".").last()

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
