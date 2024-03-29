package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import org.apache.commons.lang3.RandomStringUtils
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.config.GsonConfig
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.Alias
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.IncentiveLevelDto
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.ProfileInformation
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.nomis.dto.translate
import uk.gov.justice.digital.hmpps.prisonersearch.search.readResourceAsText
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

data class PrisonerBuilder(
  val prisonerNumber: String = generatePrisonerNumber(),
  val bookingId: Long? = generateBookingId(),
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
)

data class IncentiveLevelBuilder(
  val levelCode: String = "STD",
  val levelDescription: String = "Standard",
  val dateTime: LocalDateTime = LocalDateTime.now(),
  val nextReviewDate: LocalDate? = null,
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

fun PrisonerBuilder.toIncentiveLevel(): IncentiveLevelDto? =
  this.currentIncentive?.let {
    IncentiveLevelDto(
      iepCode = it.levelCode,
      iepLevel = it.levelDescription,
      iepTime = it.dateTime,
      nextReviewDate = it.nextReviewDate,
    )
  }

fun PrisonerBuilder.toOffenderBooking(): OffenderBooking =
  getOffenderBookingTemplate().copy(
    offenderNo = this.prisonerNumber,
    bookingId = this.bookingId,
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
        firstName = this.firstName,
        middleName = null,
        lastName = this.lastName,
        age = null,
        dob = LocalDate.parse(this.dateOfBirth),
        nameType = null,
        createDate = LocalDate.now(),
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

fun PrisonerBuilder.toPrisoner(): Prisoner =
  toPrisoner(ob = toOffenderBooking(), incentiveLevel = toIncentiveLevel(), null)

private fun getOffenderBookingTemplate(): OffenderBooking =
  GsonConfig().gson().fromJson("/templates/booking.json".readResourceAsText(), OffenderBooking::class.java)

fun toPrisoner(ob: OffenderBooking, incentiveLevel: IncentiveLevelDto?, restrictedPatientData: RestrictedPatient?) =
  Prisoner().apply { this.translate(null, ob, Result.success(incentiveLevel), restrictedPatientData) }
