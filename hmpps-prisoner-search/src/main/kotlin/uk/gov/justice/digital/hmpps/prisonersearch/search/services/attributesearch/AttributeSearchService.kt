package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest

@Component
class AttributeSearchService(
  private val attributes: Attributes,
) {

  fun search(request: AttributeSearchRequest, pageable: Pageable = Pageable.unpaged()) {
    request.validate(attributes)
    log.info("searchByAttributes called with request: $request, pageable: $pageable")
  }

  fun getAttributes() = attributes.map { it.key to it.value.toString().lastWord() }.toMap()

  private fun String.lastWord() = split(".").last()

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
