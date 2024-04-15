package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis

import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.Diffable
import org.apache.commons.lang3.builder.ToStringStyle
import java.time.LocalDate

data class OffenderBooking(
  val offenderNo: String,
  val firstName: String,
  val lastName: String,
  val dateOfBirth: LocalDate,
  val bookingId: Long? = null,
  val bookingNo: String? = null,
  val middleName: String? = null,
  val aliases: List<Alias>? = null,
  val agencyId: String? = null,
  val inOutStatus: String? = null,
  var lastMovementTypeCode: String? = null,
  var lastMovementReasonCode: String? = null,
  val religion: String? = null,
  val alerts: List<Alert>? = null,
  val assignedLivingUnit: AssignedLivingUnit? = null,
  val physicalAttributes: PhysicalAttributes? = null,
  val physicalCharacteristics: List<PhysicalCharacteristic>? = null,
  val profileInformation: List<ProfileInformation>? = null,
  val physicalMarks: List<PhysicalMark>? = null,
  val csra: String? = null,
  val categoryCode: String? = null,
  val identifiers: List<OffenderIdentifier>? = null,
  val sentenceDetail: SentenceDetail? = null,
  val mostSeriousOffence: String? = null,
  val indeterminateSentence: Boolean? = null,
  val status: String? = null,
  val legalStatus: String? = null,
  val recall: Boolean? = null,
  val imprisonmentStatus: String? = null,
  val imprisonmentStatusDescription: String? = null,
  val receptionDate: LocalDate? = null,
  val locationDescription: String? = null,
  val latestLocationId: String? = null,
) : Diffable<OffenderBooking> {
  override fun diff(other: OffenderBooking): DiffResult<OffenderBooking> = getDiffResult(this, other)
}

private fun getDiffResult(booking: OffenderBooking, other: OffenderBooking): DiffResult<OffenderBooking> =
  DiffBuilder(booking, other, ToStringStyle.JSON_STYLE).apply {
    OffenderBooking::class.members
      .filterNot { listOf("copy", "diff", "equals", "toString", "hashCode").contains(it.name) }
      .filterNot { it.name.startsWith("component") }
      .forEach { property -> append(property.name, property.call(booking), property.call(other)) }
  }.build()
