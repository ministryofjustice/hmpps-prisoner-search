package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis

import java.time.LocalDate

data class Address(
  val addressId: Long,
  val flat: String?,
  val premise: String?,
  val street: String?,
  val locality: String?,
  val town: String?,
  val postalCode: String?,
  val county: String?,
  val country: String?,
  val primary: Boolean,
  val startDate: LocalDate?,
)
