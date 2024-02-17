package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import getAttributes
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerSearchResource
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.getAllMatchers

@Component
class AttributeSearchService {

  private val attributes: Attributes = getAttributes(Prisoner::class)

  fun search(request: AttributeSearchRequest) {
    validate(request)
    log.info("searchByAttributes called with request: $request")
  }

  fun validate(request: AttributeSearchRequest) {
    request.validate()
    request.matchers.getAllMatchers().forEach { it.validate() }
    AttributeType.entries
      .flatMap { type -> request.matchers.getAllMatchers(type) }
      .forEach { typeMatcher ->
        findAttributeType(typeMatcher.attribute)
          .also { attribute -> typeMatcher.validate(attribute) }
      }
  }

  private fun findAttributeType(attributeName: String): AttributeType {
    return attributes[attributeName] ?: throw AttributeSearchException("Unknown attribute: $attributeName")
  }

  private companion object {
    private val log = LoggerFactory.getLogger(PrisonerSearchResource::class.java)
  }
}

class AttributeSearchException(message: String) : ValidationException(message)
