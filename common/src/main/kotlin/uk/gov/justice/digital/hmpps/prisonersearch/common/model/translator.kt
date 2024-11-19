@file:Suppress("ktlint:standard:filename")

package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenceHistoryDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderIdentifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Telephone
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address as NomisAddress

fun Prisoner.translate(existingPrisoner: Prisoner? = null, ob: OffenderBooking, incentiveLevel: Result<IncentiveLevel?>, restrictedPatientData: Result<RestrictedPatient?>): Prisoner {
  this.prisonerNumber = ob.offenderNo
  this.bookNumber = ob.bookingNo
  this.bookingId = ob.bookingId?.toString()
  this.pncNumber = ob.latestIdentifier("PNC")?.value
  this.pncNumberCanonicalShort = this.pncNumber?.canonicalPNCNumberShort()
  this.pncNumberCanonicalLong = this.pncNumber?.canonicalPNCNumberLong()
  this.croNumber = ob.latestIdentifier("CRO")?.value

  this.cellLocation = ob.assignedLivingUnit?.description
  this.prisonName = ob.assignedLivingUnit?.agencyName
  this.prisonId = ob.agencyId
  this.lastPrisonId = ob.latestLocationId
  this.status = ob.status
  this.inOutStatus = ob.inOutStatus
  this.lastMovementTypeCode = ob.lastMovementTypeCode
  this.lastMovementReasonCode = ob.lastMovementReasonCode

  this.category = ob.categoryCode
  this.csra = ob.csra

  this.dateOfBirth = ob.dateOfBirth
  this.title = ob.title
  this.firstName = ob.firstName
  this.middleNames = ob.middleName
  this.lastName = ob.lastName

  this.aliases =
    ob.aliases?.map { a ->
      PrisonerAlias(
        title = a.title,
        firstName = a.firstName,
        middleNames = a.middleName,
        lastName = a.lastName,
        dateOfBirth = a.dob,
        gender = a.gender,
        ethnicity = a.ethnicity,
        raceCode = a.raceCode,
      )
    }
  this.alerts =
    ob.alerts?.filter { a -> a.active }?.map { a -> PrisonerAlert(a.alertType, a.alertCode, a.active, a.expired) }

  this.gender = ob.physicalAttributes?.gender
  this.ethnicity = ob.physicalAttributes?.ethnicity
  this.raceCode = ob.physicalAttributes?.raceCode
  this.heightCentimetres = ob.physicalAttributes?.heightCentimetres
  this.weightKilograms = ob.physicalAttributes?.weightKilograms

  ob.physicalCharacteristics?.filterNot { it.detail.isNullOrBlank() }?.forEach {
    when (it.type) {
      "HAIR" -> this.hairColour = it.detail
      "R_EYE_C" -> this.rightEyeColour = it.detail
      "L_EYE_C" -> this.leftEyeColour = it.detail
      "FACIAL_HAIR" -> this.facialHair = it.detail
      "FACE" -> this.shapeOfFace = it.detail
      "BUILD" -> this.build = it.detail
      "SHOESIZE" -> this.shoeSize = it.detail?.toIntOrNull()
    }
  }
  ob.physicalMarks?.forEach { pm ->
    pm.bodyPart?.let { BodyPartDetail(it, pm.comment) }?.let { bodyPart ->
      when (pm.type) {
        "Tattoo" -> this.tattoos = this.tattoos?.plus(bodyPart) ?: listOf(bodyPart)
        "Scar" -> this.scars = this.scars?.plus(bodyPart) ?: listOf(bodyPart)
        "Mark", "Other" -> {
          this.marks = this.marks?.plus(bodyPart) ?: listOf(bodyPart)
          this.tattoos = this.tattoos.addIfCommentContains(bodyPart, "tattoo")
          this.scars = this.scars.addIfCommentContains(bodyPart, "scar")
        }
      }
    }
  }

  this.nationality = ob.profileInformation?.firstOrNull { p -> p.type == "NAT" }?.resultValue
  this.religion = ob.profileInformation?.firstOrNull { p -> p.type == "RELF" }?.resultValue
  this.maritalStatus = ob.profileInformation?.firstOrNull { p -> p.type == "MARITAL" }?.resultValue
  this.youthOffender =
    ob.profileInformation?.firstOrNull { p -> p.type == "YOUTH" }?.resultValue?.uppercase() == "YES"

  this.sentenceStartDate = ob.sentenceDetail?.sentenceStartDate
  this.confirmedReleaseDate = ob.sentenceDetail?.confirmedReleaseDate
  this.releaseDate = ob.sentenceDetail?.releaseDate
  this.sentenceExpiryDate = ob.sentenceDetail?.sentenceExpiryDate
  this.licenceExpiryDate = ob.sentenceDetail?.licenceExpiryDate
  this.homeDetentionCurfewEligibilityDate = ob.sentenceDetail?.homeDetentionCurfewEligibilityDate
  this.homeDetentionCurfewActualDate = ob.sentenceDetail?.homeDetentionCurfewActualDate
  this.homeDetentionCurfewEndDate = ob.sentenceDetail?.homeDetentionCurfewEndDate
  this.topupSupervisionStartDate = ob.sentenceDetail?.topupSupervisionStartDate
  this.topupSupervisionExpiryDate = ob.sentenceDetail?.topupSupervisionExpiryDate
  this.additionalDaysAwarded = ob.sentenceDetail?.additionalDaysAwarded
  this.nonDtoReleaseDate = ob.sentenceDetail?.nonDtoReleaseDate
  this.nonDtoReleaseDateType = ob.sentenceDetail?.nonDtoReleaseDateType
  this.tariffDate = ob.sentenceDetail?.tariffDate
  this.releaseOnTemporaryLicenceDate = ob.sentenceDetail?.releaseOnTemporaryLicenceDate

  this.receptionDate = ob.receptionDate
  this.paroleEligibilityDate = ob.sentenceDetail?.paroleEligibilityDate
  this.automaticReleaseDate =
    ob.sentenceDetail?.automaticReleaseOverrideDate ?: ob.sentenceDetail?.automaticReleaseDate
  this.postRecallReleaseDate =
    ob.sentenceDetail?.postRecallReleaseOverrideDate ?: ob.sentenceDetail?.postRecallReleaseDate
  this.conditionalReleaseDate =
    ob.sentenceDetail?.conditionalReleaseOverrideDate ?: ob.sentenceDetail?.conditionalReleaseDate
  this.actualParoleDate = ob.sentenceDetail?.actualParoleDate

  this.mostSeriousOffence = ob.mostSeriousOffence
  this.recall = ob.recall
  this.legalStatus = ob.legalStatus
  this.imprisonmentStatus = ob.imprisonmentStatus
  this.imprisonmentStatusDescription = ob.imprisonmentStatusDescription
  this.indeterminateSentence = ob.indeterminateSentence

  // setting of locationDescription in RED index:
  //                                         |       RP event                        | normal event (failure forced)        |
  //  RP call success and prisoner is RP     |    construct *                        |      does not happen                 |
  //  RP call success and prisoner is not RP |    use previous                       |      does not happen                 |
  //  RP call failure and prisoner is RP     |    use previous        (gets retried) |        use previous    (not retried) |
  //  RP call failure and prisoner is not RP | ob.locationDescription (gets retried) | ob.locationDescription (not retried) |

  // setting of locationDescription in GREEN/BLUE index:
  //                                         |     any event                 |
  //  RP call success and prisoner is RP     |   construct *                 |
  //  RP call success and prisoner is not RP | ob.locationDescription        |
  //  RP call failure and prisoner is RP     |   use previous (gets retried) |
  //  RP call failure and prisoner is not RP |   use previous (gets retried) |

  // * construct means set it to "${ob.locationDescription} - discharged to ${it.dischargedHospital?.description}"

  restrictedPatientData.onSuccess { rp ->
    setLocationDescription(rp, ob)
    setRestrictedPatientFields(rp)
  }.onFailure {
    // couldn't grab the restricted patient data, so copy across the previous information
    this.restrictedPatient = existingPrisoner?.restrictedPatient == true

    // When this is a non-RP event and the prisoner is not an RP, use the ob data
    this.locationDescription = if (restrictedPatient) existingPrisoner?.locationDescription else ob.locationDescription

    this.supportingPrisonId = existingPrisoner?.supportingPrisonId
    this.dischargedHospitalId = existingPrisoner?.dischargedHospitalId
    this.dischargedHospitalDescription = existingPrisoner?.dischargedHospitalDescription
    this.dischargeDate = existingPrisoner?.dischargeDate
    this.dischargeDetails = existingPrisoner?.dischargeDetails
  }

  this.currentIncentive = incentiveLevel.map { it.toCurrentIncentive() }.getOrElse { existingPrisoner?.currentIncentive }

  this.addresses = ob.addresses?.map { it.toAddress() }
  this.emailAddresses = ob.emailAddresses?.map { EmailAddress(it.email) }
  this.phoneNumbers = ob.phones?.toPhoneNumbers()

  this.identifiers = ob.allIdentifiers?.toIdentifiers()

  this.allConvictedOffences = ob.allConvictedOffences?.toOffences(ob.bookingId)

  return this
}

fun IncentiveLevel?.toCurrentIncentive(): CurrentIncentive? = this?.let {
  CurrentIncentive(
    level = IncentiveLevel(it.iepCode, it.iepLevel),
    nextReviewDate = it.nextReviewDate,
    // ES only stores to the second
    dateTime = it.iepTime.withNano(0),
  )
}

fun Prisoner.setLocationDescription(rp: RestrictedPatient?, ob: OffenderBooking) {
  this.locationDescription = rp
    ?.let { "${ob.locationDescription} - discharged to ${it.dischargedHospital?.description}" }
    ?: ob.locationDescription
}

fun Prisoner.setRestrictedPatientFields(rp: RestrictedPatient?) {
  this.restrictedPatient = rp != null
  this.supportingPrisonId = rp?.supportingPrisonId
  this.dischargedHospitalId = rp?.dischargedHospital?.agencyId
  this.dischargedHospitalDescription = rp?.dischargedHospital?.description
  this.dischargeDate = rp?.dischargeDate
  this.dischargeDetails = rp?.dischargeDetails
}

private fun List<BodyPartDetail>?.addIfCommentContains(bodyPart: BodyPartDetail, keyword: String): List<BodyPartDetail>? =
  if (bodyPart.comment?.lowercase()?.contains(keyword) == true) {
    bodyPart.copy().let {
      this?.plus(it) ?: listOf(it)
    }
  } else {
    this
  }

private fun NomisAddress.toAddress(): Address {
  if (noFixedAddress) {
    return Address(
      fullAddress = "No fixed address",
      primaryAddress = primary,
      startDate = startDate,
      noFixedAddress = true,
    )
  }
  val address = mutableListOf<String>()

  fun MutableList<String>.addIfNotEmpty(value: String?) {
    if (!value.isNullOrBlank()) {
      add(value.trim())
    }
  }

  // Append "Flat" if there is one
  if (!flat.isNullOrBlank()) {
    address.add("Flat ${flat.trim()}")
  }
  // Don't separate a numeric premise from the street, only if it's a name
  val hasPremise = !premise.isNullOrBlank()
  val premiseIsNumber = premise?.all { char -> char.isDigit() } ?: false
  val hasStreet = !street.isNullOrBlank()
  when {
    hasPremise && premiseIsNumber && hasStreet -> address.add("$premise $street")
    hasPremise && !premiseIsNumber && hasStreet -> address.add("$premise, $street")
    hasPremise -> address.add(premise)
    hasStreet -> address.add(street)
  }
  // Add others if they exist
  address.addIfNotEmpty(locality)
  address.addIfNotEmpty(town)
  address.addIfNotEmpty(county)
  address.addIfNotEmpty(postalCode)
  address.addIfNotEmpty(country)

  return Address(
    fullAddress = address.joinToString(", "),
    postalCode = postalCode,
    startDate = startDate,
    primaryAddress = primary,
    phoneNumbers = phones?.toPhoneNumbers(),
  )
}

private fun List<Telephone>.toPhoneNumbers(): List<PhoneNumber> =
  filter { SupportedPhoneNumberTypes.supports(it.type) }
    .map { PhoneNumber(it.type, it.number.extractNumbers()) }

private enum class SupportedPhoneNumberTypes {
  HOME,
  MOB,
  ;

  companion object {
    fun supports(type: String) = SupportedPhoneNumberTypes.entries.map { it.toString() }.contains(type)
  }
}

private fun String.extractNumbers() =
  split(Regex("\\D+"))
    .filter { it.isNotBlank() }
    .joinToString(separator = "")

private fun List<OffenderIdentifier>?.toIdentifiers(): List<Identifier>? =
  this?.mapNotNull {
    when (it.type) {
      "PNC" -> Identifier("PNC", it.value.toPncNumber(), it.issuedDate, it.issuedAuthorityText, it.whenCreated.withNano(0))
      "CRO", "DL", "NINO" -> Identifier(it.type, it.value, it.issuedDate, it.issuedAuthorityText, it.whenCreated.withNano(0))
      else -> null
    }
  }?.sortedWith(compareBy<Identifier> { it.createdDateTime }.thenBy { it.type })

private fun String.toPncNumber(): String = if (this.isPNCNumber()) this.canonicalPNCNumberShort()!! else this

private fun List<OffenceHistoryDetail>?.toOffences(latestBookingId: Long?): List<Offence>? =
  this?.map {
    Offence(
      statuteCode = it.statuteCode,
      offenceCode = it.offenceCode,
      offenceDescription = it.offenceDescription,
      offenceDate = it.offenceDate,
      latestBooking = it.bookingId == latestBookingId,
      sentenceStartDate = it.sentenceStartDate,
      primarySentence = it.primarySentence,
    )
  }
