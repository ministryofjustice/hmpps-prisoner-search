package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis

import java.time.LocalDate

data class OffenderBookingOld(
  val offenderNo: String,
  val firstName: String,
  val lastName: String,
  val dateOfBirth: LocalDate,
  val activeFlag: Boolean,
  val bookingId: Long? = null,
  val bookingNo: String? = null,
  val middleName: String? = null,
  val aliases: List<Alias>? = null,
  val agencyId: String? = null,
  val inOutStatus: String? = null,
  var lastMovementTypeCode: String? = null,
  var lastMovementReasonCode: String? = null,
  val religion: String? = null,
  val language: String? = null,
  val alerts: List<Alert>? = null,
  val assignedLivingUnit: AssignedLivingUnit? = null,
  val facialImageId: Long? = null,
  val age: Int? = null,
  val physicalAttributes: PhysicalAttributes? = null,
  val physicalCharacteristics: List<PhysicalCharacteristic>? = null,
  val profileInformation: List<ProfileInformation>? = null,
  val physicalMarks: List<PhysicalMark>? = null,
  val assessments: List<Assessment>? = null,
  val csra: String? = null,
  val categoryCode: String? = null,
  val birthPlace: String? = null,
  val birthCountryCode: String? = null,
  val identifiers: List<OffenderIdentifier>? = null,
  val sentenceDetail: SentenceDetail? = null,
  val offenceHistory: List<OffenceHistoryDetail>? = null,
  val sentenceTerms: List<SentenceTerm>? = null,
  val status: String? = null,
  val legalStatus: String? = null,
  val recall: Boolean? = null,
  val imprisonmentStatus: String? = null,
  val imprisonmentStatusDescription: String? = null,
  val personalCareNeeds: List<PersonalCareNeed>? = null,
  val receptionDate: LocalDate? = null,
  val locationDescription: String? = null,
  val latestLocationId: String? = null,
) {
  fun toOffenderBooking(): OffenderBooking {
    return OffenderBooking(
      offenderNo,
      firstName,
      lastName,
      dateOfBirth,
      bookingId,
      bookingNo,
      middleName,
      aliases,
      agencyId,
      inOutStatus,
      lastMovementTypeCode,
      lastMovementReasonCode,
      religion,
      alerts,
      assignedLivingUnit,
      physicalAttributes,
      physicalCharacteristics,
      profileInformation,
      physicalMarks,
      csra,
      categoryCode,
      identifiers,
      sentenceDetail,
      offenceHistory?.firstOrNull { off -> off.mostSerious && off.bookingId == bookingId }?.offenceDescription,
      sentenceTerms?.any { st -> st.lifeSentence && st.bookingId == bookingId },
      status,
      legalStatus,
      recall,
      imprisonmentStatus,
      imprisonmentStatusDescription,
      receptionDate,
      locationDescription,
      latestLocationId,
    )
  }
}
