package uk.gov.justice.digital.hmpps.prisonersearch.common.nomis

import java.time.LocalDate
import java.time.LocalDateTime

data class OffenderBooking(
  val offenderNo: String,
  val offenderId: Long,
  val firstName: String = "Fred",
  val lastName: String = "Bloggs",
  val dateOfBirth: LocalDate = LocalDate.of(1976, 5, 15),
  val title: String? = null,
  val bookingId: Long? = null,
  val bookingNo: String? = null,
  val middleName: String? = null,
  val aliases: List<Alias>? = null,
  val agencyId: String? = null,
  val inOutStatus: String? = null,
  var lastMovementTypeCode: String? = null,
  var lastMovementReasonCode: String? = null,
  var lastMovementTime: LocalDateTime? = null,
  var lastAdmissionTime: LocalDateTime? = null,
  val religion: String? = null,
  val assignedLivingUnit: AssignedLivingUnit? = null,
  val physicalAttributes: PhysicalAttributes? = null,
  val physicalCharacteristics: List<PhysicalCharacteristic>? = null,
  val profileInformation: List<ProfileInformation>? = null,
  val physicalMarks: List<PhysicalMark>? = null,
  val csra: String? = null,
  val categoryCode: String? = null,
  val allIdentifiers: List<OffenderIdentifier>? = null,
  val sentenceDetail: SentenceDetail? = null,
  val mostSeriousOffence: String? = null,
  val indeterminateSentence: Boolean? = null,
  val status: String? = null,
  val legalStatus: String? = null,
  val recall: Boolean? = null,
  val imprisonmentStatus: String? = null,
  val imprisonmentStatusDescription: String? = null,
  val convictedStatus: String? = null,
  val receptionDate: LocalDate? = null,
  val locationDescription: String? = null,
  val latestLocationId: String? = null,
  val addresses: List<Address>? = null,
  val emailAddresses: List<EmailAddress>? = null,
  val phones: List<Telephone>? = null,
  val allConvictedOffences: List<OffenceHistoryDetail>? = null,
) {
  fun latestIdentifier(type: String) = allIdentifiers
    ?.filter { it.type == type }
    ?.takeIf { it.isNotEmpty() }
    ?.maxBy { it.whenCreated }

  fun identifiersForActiveOffender(type: String) = allIdentifiers
    ?.filter { it.offenderId == offenderId }
    ?.filter { it.type == type }
}
