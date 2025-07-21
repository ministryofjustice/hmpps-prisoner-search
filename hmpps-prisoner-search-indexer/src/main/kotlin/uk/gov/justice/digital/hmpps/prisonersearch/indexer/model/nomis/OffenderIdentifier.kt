package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis

import java.time.LocalDate
import java.time.LocalDateTime

data class OffenderIdentifier(
  val offenderId: Long,
  val type: String,
  val value: String,
  val issuedAuthorityText: String?,
  val issuedDate: LocalDate?,
  val whenCreated: LocalDateTime,
)
