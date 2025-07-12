package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Address
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.BodyPartDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Identifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Offence
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PhoneNumber
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlias
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.canonicalPNCNumberLong
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.canonicalPNCNumberShort
import java.time.LocalDate
import java.time.LocalDateTime

data class PrisonerBuilder(
  val prisonerNumber: String = generatePrisonerNumber(),
  val bookingId: Long? = generateBookingId(),
  val title: String? = "Mr",
  val firstName: String = "LUCAS",
  val lastName: String = "MORALES",
  val agencyId: String = "MDI",
  val released: Boolean = false,
  val alertCodes: List<Pair<String, String>> = listOf(),
  val dateOfBirth: String = "1965-07-19",
  val cellLocation: String = "A-1-1",
  val heightCentimetres: Int? = null,
  val weightKilograms: Int? = null,
  val gender: String? = null,
  val ethnicity: String? = null,
  val raceCode: String? = null,
  val aliases: List<AliasBuilder> = listOf(),
  val physicalCharacteristics: PhysicalCharacteristicBuilder? = null,
  val physicalMarks: PhysicalMarkBuilder? = null,
  val profileInformation: ProfileInformationBuilder? = null,
  val currentIncentive: IncentiveLevelBuilder? = null,
  val category: String? = null,
  val csra: String? = null,
  val recall: Boolean? = null,
  val receptionDate: String? = null,
  val addresses: List<AddressBuilder>? = null,
  val emailAddresses: List<EmailAddressBuilder>? = null,
  val phones: List<PhoneBuilder>? = null,
  val identifiers: List<IdentifierBuilder>? = null,
  val allConvictedOffences: List<OffenceBuilder>? = null,
)

data class PhysicalCharacteristicBuilder(
  val hairColour: String? = null,
  val rightEyeColour: String? = null,
  val leftEyeColour: String? = null,
  val facialHair: String? = null,
  val shapeOfFace: String? = null,
  val build: String? = null,
  val shoeSize: Int? = null,
)

data class PhysicalMarkBuilder(
  val mark: List<BodyPartBuilder>? = null,
  val scar: List<BodyPartBuilder>? = null,
  val tattoo: List<BodyPartBuilder>? = null,
)

data class ProfileInformationBuilder(
  val religion: String? = null,
  val nationality: String? = null,
  val youthOffender: Boolean? = false,
  val maritalStatus: String? = null,
)
data class BodyPartBuilder(
  val bodyPart: String,
  val comment: String? = null,
)

data class AliasBuilder(
  val gender: String? = null,
  val ethnicity: String? = null,
  val raceCode: String? = null,
  val title: String? = null,
  val firstName: String? = null,
)

data class IncentiveLevelBuilder(
  val levelCode: String = "STD",
  val levelDescription: String = "Standard",
  val dateTime: LocalDateTime = LocalDateTime.now(),
  val nextReviewDate: LocalDate? = null,
)

data class AddressBuilder(
  val fullAddress: String? = null,
  val postalCode: String? = null,
  val primary: Boolean = true,
  val startDate: LocalDate? = null,
  val phones: List<PhoneBuilder>? = null,
)

data class EmailAddressBuilder(
  val email: String? = null,
)

data class PhoneBuilder(
  val type: String? = null,
  val number: String? = null,
)

data class IdentifierBuilder(
  val type: String,
  val value: String,
  val issuedDate: String? = null,
  val issuedAuthorityText: String? = null,
  val createdDatetime: String? = null,
)

data class OffenceBuilder(
  val statuteCode: String,
  val offenceCode: String,
  val offenceDescription: String,
  val offenceDate: LocalDate? = null,
  val bookingId: Long,
  val mostSerious: Boolean,
  val offenceSeverityRanking: Int,
  val sentenceStartDate: LocalDate?,
  val primarySentence: Boolean?,
)

fun generatePrisonerNumber(): String {
  // generate random string starting with a letter, followed by 4 numbers and 2 letters
  return "${letters(1)}${numbers(4)}${letters(2)}"
}

fun generateBookingId(): Long {
  // generate random number 8 digits
  return numbers(8).toLong()
}

fun letters(length: Int): String = RandomStringUtils.insecure().next(length, true, true)

fun numbers(length: Int): String = RandomStringUtils.insecure().next(length, false, true)

fun PrisonerBuilder.toPrisoner(): Prisoner = Prisoner().also { p ->
  p.prisonerNumber = this.prisonerNumber
  p.pncNumber = this.identifiers?.firstOrNull { it.type == "PNC" }?.value
  p.pncNumberCanonicalShort = this.identifiers?.firstOrNull { it.type == "PNC" }?.value?.canonicalPNCNumberShort()
  p.pncNumberCanonicalLong = this.identifiers?.firstOrNull { it.type == "PNC" }?.value?.canonicalPNCNumberLong()
  p.croNumber = this.identifiers?.firstOrNull { it.type == "CRO" }?.value
  p.bookingId = this.bookingId?.toString()
  p.bookNumber = "V61587"
  p.title = this.title
  p.firstName = this.firstName
  p.middleNames = null
  p.lastName = this.lastName
  p.dateOfBirth = LocalDate.parse(this.dateOfBirth)
  p.gender = this.gender
  p.ethnicity = this.ethnicity
  p.raceCode = this.raceCode
  p.youthOffender = this.profileInformation?.youthOffender
  p.maritalStatus = this.profileInformation?.maritalStatus
  p.religion = this.profileInformation?.religion
  p.nationality = this.profileInformation?.nationality
  p.smoker = null
  p.personalCareNeeds = null
  p.languages = null
  p.currentFacialImageId = null
  if (released) {
    p.status = "INACTIVE OUT"
    p.lastMovementTypeCode = "REL"
    p.lastMovementReasonCode = "HP"
    p.inOutStatus = "OUT"
    p.prisonId = "OUT"
  } else {
    p.status = "ACTIVE IN"
    p.lastMovementTypeCode = "ADM"
    p.lastMovementReasonCode = "I"
    p.inOutStatus = "IN"
    p.prisonId = this.agencyId
  }
  p.lastPrisonId = null
  p.prisonName = "Moorland Prison"
  p.cellLocation = this.cellLocation
  p.aliases = this.aliases.map {
    PrisonerAlias(
      title = it.title,
      firstName = it.firstName,
      gender = it.gender,
      raceCode = it.raceCode,
      ethnicity = it.ethnicity,
    )
  }
  p.alerts = this.alertCodes.map {
    PrisonerAlert(
      alertType = it.first,
      alertCode = it.second,
      expired = false,
      active = true,
    )
  }
  p.csra = this.csra
  p.category = this.category
  p.complexityOfNeedLevel = null
  p.legalStatus = "REMAND"
  p.imprisonmentStatus = "LIFE"
  p.imprisonmentStatusDescription = "Life imprisonment"
  p.convictedStatus = "Remand"
  p.mostSeriousOffence = null
  p.recall = this.recall
  p.indeterminateSentence = null
  p.sentenceStartDate = null
  p.releaseDate = null
  p.confirmedReleaseDate = null
  p.sentenceExpiryDate = null
  p.licenceExpiryDate = null
  p.homeDetentionCurfewEligibilityDate = null
  p.homeDetentionCurfewActualDate = null
  p.homeDetentionCurfewEndDate = null
  p.topupSupervisionStartDate = null
  p.topupSupervisionExpiryDate = null
  p.additionalDaysAwarded = null
  p.nonDtoReleaseDate = null
  p.nonDtoReleaseDateType = null
  p.receptionDate = this.receptionDate?.let { LocalDate.parse(it) }
  p.lastAdmissionDate = null
  p.paroleEligibilityDate = null
  p.automaticReleaseDate = null
  p.postRecallReleaseDate = null
  p.conditionalReleaseDate = null
  p.actualParoleDate = null
  p.tariffDate = null
  p.releaseOnTemporaryLicenceDate = null

  // take into account restricted patient here
  p.locationDescription = null
  p.restrictedPatient = false
  p.supportingPrisonId = null
  p.dischargedHospitalId = null
  p.dischargedHospitalDescription = null
  p.dischargeDate = null
  p.dischargeDetails = null
  p.currentIncentive = this.currentIncentive?.let {
    CurrentIncentive(
      level = uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel(it.levelCode, it.levelDescription),
      nextReviewDate = it.nextReviewDate,
      dateTime = it.dateTime,
    )
  }
  p.heightCentimetres = this.heightCentimetres
  p.weightKilograms = this.weightKilograms
  p.hairColour = this.physicalCharacteristics?.hairColour
  p.rightEyeColour = this.physicalCharacteristics?.rightEyeColour
  p.leftEyeColour = this.physicalCharacteristics?.leftEyeColour
  p.facialHair = this.physicalCharacteristics?.facialHair
  p.shapeOfFace = this.physicalCharacteristics?.shapeOfFace
  p.build = this.physicalCharacteristics?.build
  p.shoeSize = this.physicalCharacteristics?.shoeSize
  p.tattoos = this.physicalMarks?.tattoo?.map { BodyPartDetail(it.bodyPart, it.comment) }
  p.scars = this.physicalMarks?.scar?.map { BodyPartDetail(it.bodyPart, it.comment) }
  p.marks = this.physicalMarks?.mark?.map { BodyPartDetail(it.bodyPart, it.comment) }
  p.addresses = this.addresses?.map {
    Address(
      fullAddress = it.fullAddress,
      postalCode = it.postalCode,
      startDate = it.startDate,
      primaryAddress = it.primary,
      phoneNumbers = it.phones?.map { pn -> PhoneNumber(pn.type, pn.number?.split(Regex("\\D+"))?.filter { f -> f.isNotBlank() }?.joinToString(separator = "")) },
      noFixedAddress = false,
    )
  }
  p.emailAddresses = this.emailAddresses?.map { uk.gov.justice.digital.hmpps.prisonersearch.common.model.EmailAddress(it.email) }
  p.phoneNumbers = this.phones?.map {
    PhoneNumber(
      type = it.type,
      number = it.number?.split(Regex("\\D+"))?.filter { it.isNotBlank() }?.joinToString(separator = ""),
    )
  }
  p.identifiers = this.identifiers?.map {
    Identifier(
      type = it.type,
      value = it.value,
      issuedDate = it.issuedDate?.let { id -> LocalDate.parse(id) },
      issuedAuthorityText = it.issuedAuthorityText,
      createdDateTime = it.createdDatetime?.let { cd -> LocalDateTime.parse(cd) },
    )
  }
  p.allConvictedOffences = this.allConvictedOffences?.map {
    Offence(
      statuteCode = it.statuteCode,
      offenceCode = it.offenceCode,
      offenceDescription = it.offenceDescription,
      offenceDate = it.offenceDate,
      latestBooking = it.bookingId == this.bookingId,
      sentenceStartDate = it.sentenceStartDate,
      primarySentence = it.primarySentence,
    )
  }
}
