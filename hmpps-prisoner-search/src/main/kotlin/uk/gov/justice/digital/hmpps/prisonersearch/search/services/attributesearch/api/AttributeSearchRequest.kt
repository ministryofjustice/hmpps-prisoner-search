package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.AttributeSearchException

data class AttributeSearchRequest(
  val matchers: List<Matchers>,
) {
  fun validate() {
    if (matchers.isEmpty()) {
      throw AttributeSearchException("At least one matcher must be provided")
    }
  }
}
