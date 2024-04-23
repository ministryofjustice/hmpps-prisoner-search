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
  override fun diff(newEndpointBooking: OffenderBooking): DiffResult<OffenderBooking> = getDiffResult(this, newEndpointBooking)
}

private fun getDiffResult(oldEndpointBooking: OffenderBooking, newEndpointBooking: OffenderBooking): DiffResult<OffenderBooking> {
  // The old endpoint sometimes returns null for additionalDaysAwarded and sometimes 0 (there's some old data with 0 value in OFFENDER_KEY_DATE_ADJUSTS.ADJUST_DAYS but for recent data it's missing entirely, hence null)
  // We don't want this difference to appear as a false positive so just set ADA to 0 before doing the diff
  val additionalDaysNull = oldEndpointBooking.sentenceDetail?.additionalDaysAwarded == null
  val oldBooking = if (additionalDaysNull) {
    oldEndpointBooking.copy(sentenceDetail = oldEndpointBooking.sentenceDetail?.copy(additionalDaysAwarded = 0))
  } else {
    oldEndpointBooking
  }
  return DiffBuilder(oldBooking, newEndpointBooking, ToStringStyle.JSON_STYLE).apply {
    OffenderBooking::class.members
      .filterNot { listOf("copy", "diff", "equals", "toString", "hashCode").contains(it.name) }
      // These show differences because of either the order a list is returned or because they have additional data from the old endpoint that we're not interested in. So we'll ignore them as we're trying to find genuine differences.
      .filterNot { listOf("alerts", "profileInformation", "identifiers").contains(it.name) }
      .filterNot { it.name.startsWith("component") }
      .forEach { property -> append(property.name, property.call(oldBooking), property.call(newEndpointBooking)) }
  }.build()
}
