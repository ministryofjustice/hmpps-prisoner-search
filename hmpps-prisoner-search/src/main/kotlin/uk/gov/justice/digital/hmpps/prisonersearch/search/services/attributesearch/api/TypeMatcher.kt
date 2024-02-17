package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeType

sealed interface TypeMatcher {
  val attribute: String
  fun validate(attributeType: AttributeType)
}