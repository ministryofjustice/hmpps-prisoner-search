package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

data class BooleanMatcher(
  override val attribute: String,
  val condition: Boolean,
) : TypeMatcher<Boolean>
