package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearch.search.resource.PrisonerSearchResource
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.getAllMatchers

@Component
class AttributeSearchService(
  private val attributes: Attributes,
) {

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
        typeMatcher.validateType()
        typeMatcher.validate()
      }
  }

  private fun TypeMatcher.validateType() {
    val matcherType = AttributeType.findByMatcher(this::class)
    val attributeType = findAttributeType(attribute)
    if (matcherType != attributeType) {
      throw AttributeSearchException("Attribute $attribute is not of type ${matcherType.name}")
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
