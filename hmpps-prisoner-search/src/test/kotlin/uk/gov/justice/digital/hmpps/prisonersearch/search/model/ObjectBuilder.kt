package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.translate
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Alias
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.EmailAddress
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenceHistoryDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderIdentifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.ProfileInformation
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Telephone
import uk.gov.justice.digital.hmpps.prisonersearch.search.config.GsonConfig
import uk.gov.justice.digital.hmpps.prisonersearch.search.readResourceAsText
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address as NomisAddress

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
  val flat: String? = null,
  val premise: String? = null,
  val street: String? = null,
  val locality: String? = null,
  val town: String? = null,
  val county: String? = null,
  val postalCode: String? = null,
  val country: String? = null,
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
)

fun generatePrisonerNumber(): String {
  // generate random string starting with a letter, followed by 4 numbers and 2 letters
  return "${letters(1)}${numbers(4)}${letters(2)}"
}

fun generateBookingId(): Long {
  // generate random number 8 digits
  return numbers(8).toLong()
}

fun letters(length: Int): String {
  return RandomStringUtils.random(length, true, true)
}

fun numbers(length: Int): String {
  return RandomStringUtils.random(length, false, true)
}

fun PrisonerBuilder.toIncentiveLevel(): IncentiveLevel? =
  this.currentIncentive?.let {
    IncentiveLevel(
      iepCode = it.levelCode,
      iepLevel = it.levelDescription,
      iepTime = it.dateTime,
      nextReviewDate = it.nextReviewDate,
    )
  }

fun PrisonerBuilder.toOffenderBooking(): OffenderBooking {
  val offenderId = Random.nextLong()
  return getOffenderBookingTemplate().copy(
    offenderNo = this.prisonerNumber,
    bookingId = this.bookingId,
    offenderId = offenderId,
    title = this.title,
    firstName = this.firstName,
    lastName = this.lastName,
    agencyId = this.agencyId,
    dateOfBirth = LocalDate.parse(this.dateOfBirth),
    physicalAttributes = PhysicalAttributes(
      gender = this.gender,
      raceCode = null,
      ethnicity = this.ethnicity,
      heightFeet = null,
      heightInches = null,
      heightMetres = null,
      heightCentimetres = this.heightCentimetres,
      weightPounds = null,
      weightKilograms = this.weightKilograms,
    ),
    assignedLivingUnit = AssignedLivingUnit(
      agencyId = this.agencyId,
      locationId = Random.nextLong(),
      description = this.cellLocation,
      agencyName = "$agencyId (HMP)",
    ),
    alerts = this.alertCodes.map { (type, code) ->
      Alert(
        alertId = Random.nextLong(),
        offenderNo = this.prisonerNumber,
        alertCode = code,
        alertCodeDescription = "Code description for $code",
        alertType = type,
        alertTypeDescription = "Type Description for $type",
        // In search all alerts are not expired and active
        expired = false,
        active = true,
        dateCreated = LocalDate.now(),
      )
    },
    aliases = this.aliases.map { a ->
      Alias(
        gender = a.gender,
        ethnicity = a.ethnicity,
        title = a.title ?: this.title,
        firstName = a.firstName ?: this.firstName,
        middleName = null,
        lastName = this.lastName,
        age = null,
        dob = LocalDate.parse(this.dateOfBirth),
        nameType = null,
        createDate = LocalDate.now(),
        offenderId = Random.nextLong(),
      )
    },
    physicalCharacteristics = mutableListOf<PhysicalCharacteristic>().also { pcs ->
      this.physicalCharacteristics?.hairColour?.let {
        pcs.add(PhysicalCharacteristic("HAIR", "Hair Colour", it, null))
      }
      this.physicalCharacteristics?.rightEyeColour?.let {
        pcs.add(PhysicalCharacteristic("R_EYE_C", "Right Eye Colour", it, null))
      }
      this.physicalCharacteristics?.leftEyeColour?.let {
        pcs.add(PhysicalCharacteristic("L_EYE_C", "Left Eye Colour", it, null))
      }
      this.physicalCharacteristics?.facialHair?.let {
        pcs.add(PhysicalCharacteristic("FACIAL_HAIR", "Facial Hair", it, null))
      }
      this.physicalCharacteristics?.shapeOfFace?.let {
        pcs.add(PhysicalCharacteristic("FACE", "Shape of Face", it, null))
      }
      this.physicalCharacteristics?.build?.let {
        pcs.add(PhysicalCharacteristic("BUILD", "Build", it, null))
      }
      this.physicalCharacteristics?.shoeSize?.let {
        pcs.add(PhysicalCharacteristic("SHOESIZE", "Shoe Size", it.toString(), null))
      }
    },
    physicalMarks = mutableListOf<PhysicalMark>().also { pms ->
      this.physicalMarks?.tattoo?.forEach {
        pms.add(PhysicalMark("Tattoo", null, it.bodyPart, null, it.comment, null))
      }
      this.physicalMarks?.mark?.forEach {
        pms.add(PhysicalMark("Mark", null, it.bodyPart, null, it.comment, null))
      }
      this.physicalMarks?.scar?.forEach {
        pms.add(PhysicalMark("Scar", null, it.bodyPart, null, it.comment, null))
      }
    },
    profileInformation = mutableListOf<ProfileInformation>().also { pi ->
      profileInformation?.religion?.let {
        pi.add(ProfileInformation(type = "RELF", question = "Religion", resultValue = it))
      }
      profileInformation?.nationality?.let {
        pi.add(ProfileInformation(type = "NAT", question = "Nationality?", resultValue = it))
      }
      profileInformation?.youthOffender?.let {
        pi.add(ProfileInformation(type = "YOUTH", question = "Youth Offender?", resultValue = if (it) "YES" else "NO"))
      }
      profileInformation?.maritalStatus?.let {
        pi.add(ProfileInformation(type = "MARITAL", question = "Marital Status?", resultValue = it))
      }
    },
    categoryCode = category,
    csra = csra,
    recall = recall,
    receptionDate = receptionDate?.let { LocalDate.parse(it) },
    addresses = addresses?.map {
      NomisAddress(
        addressId = numbers(length = 5).toLong(),
        flat = it.flat,
        premise = it.premise,
        street = it.street,
        locality = it.locality,
        town = it.town,
        postalCode = it.postalCode,
        county = it.county,
        country = it.country,
        primary = it.primary,
        startDate = it.startDate,
        phones = it.phones?.map { Telephone(type = it.type!!, number = it.number!!) },
      )
    },
    emailAddresses = emailAddresses?.filter { it.email != null }?.map { EmailAddress(it.email!!) },
    phones = phones?.map { Telephone(type = it.type!!, number = it.number!!) },
    allIdentifiers = identifiers?.map {
      OffenderIdentifier(
        type = it.type,
        value = it.value,
        issuedDate = it.issuedDate?.let { LocalDate.parse(it) },
        issuedAuthorityText = it.issuedAuthorityText,
        whenCreated = it.createdDatetime?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(),
        offenderId = offenderId,
      )
    },
    allConvictedOffences = allConvictedOffences?.map {
      OffenceHistoryDetail(
        statuteCode = it.statuteCode,
        offenceCode = it.offenceCode,
        offenceDescription = it.offenceDescription,
        offenceDate = it.offenceDate,
        offenceRangeDate = null,
        bookingId = it.bookingId,
        mostSerious = it.mostSerious,
        offenceSeverityRanking = it.offenceSeverityRanking,
      )
    },
  ).let {
    if (released) {
      it.copy(
        status = "INACTIVE OUT",
        lastMovementTypeCode = "REL",
        lastMovementReasonCode = "HP",
        inOutStatus = "OUT",
        agencyId = "OUT",
      )
    } else {
      it.copy(
        lastMovementTypeCode = "ADM",
        lastMovementReasonCode = "I",
      )
    }
  }
}

fun PrisonerBuilder.toPrisoner(): Prisoner =
  toPrisoner(ob = toOffenderBooking(), incentiveLevel = toIncentiveLevel(), null)

private fun getOffenderBookingTemplate(): OffenderBooking =
  GsonConfig().gson().fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)

fun toPrisoner(ob: OffenderBooking, incentiveLevel: IncentiveLevel?, restrictedPatientData: RestrictedPatient?) =
  Prisoner().apply { this.translate(null, ob, Result.success(incentiveLevel), Result.success(restrictedPatientData)) }
