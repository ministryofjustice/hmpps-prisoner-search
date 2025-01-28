package uk.gov.justice.digital.hmpps.prisonersearch.search.services

import uk.gov.justice.digital.hmpps.prisonersearch.common.model.canonicalPNCNumber

internal fun String.prisonerNumberOrCanonicalPNCNumber(): String = when {
  isPrisonerNumber() -> this.uppercase()
  else -> this.canonicalPNCNumber()
}

internal fun String.isPrisonerNumber() = matches("^[a-zA-Z]\\d{4}[a-zA-Z]{2}$".toRegex())
