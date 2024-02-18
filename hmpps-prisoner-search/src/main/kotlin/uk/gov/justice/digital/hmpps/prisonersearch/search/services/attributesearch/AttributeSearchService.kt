package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

@Component
class AttributeSearchService(
  private val attributes: Attributes,
) {

  fun search(request: AttributeSearchRequest) {
    request.validate(attributes)
    log.info("searchByAttributes called with request: $request")
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
