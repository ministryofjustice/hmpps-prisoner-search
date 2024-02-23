package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.BodyPartDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.SentenceDetail
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {
    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth, false),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.prisonerNumber).isEqualTo("A1234AA")
    assertThat(prisoner.firstName).isEqualTo("Fred")
    assertThat(prisoner.lastName).isEqualTo("Bloggs")
    assertThat(prisoner.dateOfBirth).isEqualTo(dateOfBirth)
    assertThat(prisoner.bookingId).isNull()
  }

  @Test
  fun `topupSupervisionExpiryDate is present`() {
    val tseDate = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionExpiryDate = tseDate),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.topupSupervisionExpiryDate).isEqualTo(tseDate)
  }

  @Test
  fun `topupSupervisionStartDate is present`() {
    val tssDate = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(topupSupervisionStartDate = tssDate),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.topupSupervisionStartDate).isEqualTo(tssDate)
  }

  @Test
  fun `homeDetentionCurfewEndDate is present`() {
    val hdcend = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(homeDetentionCurfewEndDate = hdcend),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.homeDetentionCurfewEndDate).isEqualTo(hdcend)
  }

  @Test
  fun `when a prisoner has a sentence with dateOverride for conditionalRelease, automaticRelease and postRecallRelease then corresponding overrideDate is used`() {
    val conditionalReleaseOverrideDate = LocalDate.now().plusMonths(3)
    val automaticReleaseOverrideDate = LocalDate.now().plusMonths(2)
    val postRecallReleaseOverrideDate = LocalDate.now().plusMonths(1)
    val releaseDate = LocalDate.now().plusMonths(5)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = releaseDate,
          conditionalReleaseOverrideDate = conditionalReleaseOverrideDate,
          automaticReleaseDate = releaseDate,
          automaticReleaseOverrideDate = automaticReleaseOverrideDate,
          postRecallReleaseDate = releaseDate,
          postRecallReleaseOverrideDate = postRecallReleaseOverrideDate,
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseOverrideDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseOverrideDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseOverrideDate)
  }

  @Test
  fun `when a prisoner has a sentence with no dateOverride for conditionalRelease, automaticRelease and postRecallRelease then corresponding releaseDate is used`() {
    val conditionalReleaseDate = LocalDate.now().plusMonths(5)
    val automaticReleaseDate = LocalDate.now().plusMonths(4)
    val postRecallReleaseDate = LocalDate.now().plusMonths(3)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = conditionalReleaseDate,
          automaticReleaseDate = automaticReleaseDate,
          postRecallReleaseDate = postRecallReleaseDate,
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseDate)
  }

  @Test
  fun `imprisonmentStatus and description are present`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        imprisonmentStatus = "LIFE",
        imprisonmentStatusDescription = "Serving Life Imprisonment",
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.imprisonmentStatus).isEqualTo("LIFE")
    assertThat(prisoner.imprisonmentStatusDescription).isEqualTo("Serving Life Imprisonment")
  }

  @Test
  fun `maps alerts correctly`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        false,
        alerts = listOf(
          Alert(
            alertId = 1,
            active = true,
            expired = false,
            alertCode = "x-code",
            alertType = "x-type",
            dateCreated = LocalDate.now(),
          ),
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.alerts?.first())
      .extracting("alertType", "alertCode", "active", "expired")
      .contains("x-type", "x-code", true, false)
  }

  @Test
  internal fun `current incentive is mapped`() {
    val prisoner = Prisoner().translate(
      ob = aBooking(),
      incentiveLevel = Result.success(
        IncentiveLevel(
          iepCode = "STD",
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
          nextReviewDate = LocalDate.parse("2022-02-02"),
        ),
      ),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.currentIncentive).isNotNull
    assertThat(prisoner.currentIncentive?.level).isNotNull
    assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
    assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
  }

  @Test
  internal fun `current incentive is mapped when there is no failure`() {
    val prisoner = Prisoner().translate(
      null,
      aBooking(),
      Result.success(
        IncentiveLevel(
          iepCode = "STD",
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
          nextReviewDate = LocalDate.parse("2022-02-02"),
        ),
      ),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.currentIncentive).isNotNull
    assertThat(prisoner.currentIncentive?.level).isNotNull
    assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
    assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
  }

  @Test
  internal fun `restricted patient data is mapped`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(locationDescription = "OUT"),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(
        RestrictedPatient(
          supportingPrisonId = "MDI",
          dischargedHospital = Agency(
            agencyId = "HAZLWD",
            agencyType = "HSHOSP",
            active = true,
            description = "Hazelwood Hospital",
          ),
          dischargeDate = LocalDate.now(),
          dischargeDetails = "Getting worse",
        ),
      ),
    )

    assertThat(prisoner.restrictedPatient).isTrue
    assertThat(prisoner.supportingPrisonId).isEqualTo("MDI")
    assertThat(prisoner.dischargedHospitalId).isEqualTo("HAZLWD")
    assertThat(prisoner.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
    assertThat(prisoner.dischargeDate).isEqualTo(LocalDate.now())
    assertThat(prisoner.dischargeDetails).isEqualTo("Getting worse")
    assertThat(prisoner.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
  }

  @Test
  internal fun `restricted patient data can be null`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(locationDescription = "OUT"),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.restrictedPatient).isFalse
    assertThat(prisoner.supportingPrisonId).isNull()
    assertThat(prisoner.dischargedHospitalId).isNull()
    assertThat(prisoner.dischargedHospitalDescription).isNull()
    assertThat(prisoner.dischargeDate).isNull()
    assertThat(prisoner.dischargeDetails).isNull()
    assertThat(prisoner.locationDescription).isEqualTo("OUT")
  }

  @Test
  fun `should map last prison ID`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(latestLocationId = "LEI"),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    assertThat(prisoner.lastPrisonId).isEqualTo("LEI")
  }

  @Nested
  inner class WithIncentiveLevelFailure {
    @Test
    internal fun `will fall back to old level when present`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = Result.success(
          IncentiveLevel(
            iepCode = "STD",
            iepLevel = "Standard",
            iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
            nextReviewDate = LocalDate.parse("2022-02-02"),
          ),
        ),
        restrictedPatientData = Result.success(null),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.currentIncentive).isNotNull
      assertThat(prisoner.currentIncentive?.level).isNotNull
      assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
      assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
      assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
      assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.currentIncentive).isNull()
    }

    @Test
    internal fun `will fall back to null when the previous record's incentive was null`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.currentIncentive).isNull()
    }
  }

  @Nested
  inner class WithRestrictedPatientFailure {
    @Test
    internal fun `will fall back to old data when present`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(
          RestrictedPatient(
            supportingPrisonId = "MDI",
            dischargedHospital = Agency(
              agencyId = "HAZLWD",
              agencyType = "HSHOSP",
              active = true,
              description = "Hazelwood Hospital",
            ),
            dischargeDate = LocalDate.now(),
            dischargeDetails = "Getting worse",
          ),
        ),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.failure(RuntimeException("It has gone badly wrong")),
      )

      assertThat(prisoner.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
      assertThat(prisoner.restrictedPatient).isTrue
      assertThat(prisoner.supportingPrisonId).isEqualTo("MDI")
      assertThat(prisoner.dischargedHospitalId).isEqualTo("HAZLWD")
      assertThat(prisoner.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
      assertThat(prisoner.dischargeDate).isEqualTo(LocalDate.now())
      assertThat(prisoner.dischargeDetails).isEqualTo("Getting worse")
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = aBooking().copy(locationDescription = "previous location"),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.restrictedPatient).isFalse()
      assertThat(prisoner.locationDescription).isEqualTo("previous location")
      assertThat(prisoner.supportingPrisonId).isNull()
    }

    @Test
    internal fun `will fall back to null when the previous record's restricted patient data was null`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.failure(RuntimeException("It has gone badly wrong")),
      )

      assertThat(prisoner.restrictedPatient).isFalse()
      assertThat(prisoner.locationDescription).isEqualTo("OUT")
      assertThat(prisoner.supportingPrisonId).isNull()
    }
  }

  @Test
  internal fun `Physical Attributes are mapped`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        physicalAttributes = PhysicalAttributes(
          gender = "M",
          raceCode = "F",
          ethnicity = "W",
          heightFeet = 6,
          heightInches = 7,
          heightCentimetres = 200,
          weightKilograms = 100,
          weightPounds = 224,
          heightMetres = BigDecimal.TEN,
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.gender).isEqualTo("M")
    assertThat(prisoner.ethnicity).isEqualTo("W")
    assertThat(prisoner.heightCentimetres).isEqualTo(200)
    assertThat(prisoner.weightKilograms).isEqualTo(100)
  }

  @Test
  internal fun `Physical Characteristics are mapped`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        physicalCharacteristics = listOf(
          PhysicalCharacteristic("HAIR", "Hair Colour", "Red", null),
          PhysicalCharacteristic("R_EYE_C", "Right Eye Colour", "Green", null),
          PhysicalCharacteristic("L_EYE_C", "Left Eye Colour", "Hazel", null),
          PhysicalCharacteristic("FACIAL_HAIR", "Facial Hair", "Clean Shaven", null),
          PhysicalCharacteristic("FACE", "Shape of Face", "Bullet", null),
          PhysicalCharacteristic("BUILD", "Build", "Proportional", null),
          PhysicalCharacteristic("SHOESIZE", "Shoe Size", "10", null),
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.hairColour).isEqualTo("Red")
    assertThat(prisoner.rightEyeColour).isEqualTo("Green")
    assertThat(prisoner.leftEyeColour).isEqualTo("Hazel")
    assertThat(prisoner.facialHair).isEqualTo("Clean Shaven")
    assertThat(prisoner.shapeOfFace).isEqualTo("Bullet")
    assertThat(prisoner.build).isEqualTo("Proportional")
    assertThat(prisoner.shoeSize).isEqualTo(10)
  }

  @Test
  internal fun `Physical Marks are mapped`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        physicalMarks = listOf(
          PhysicalMark("Tattoo", "Left", "Elbow", "Upper", "Comment here", null),
          PhysicalMark("Tattoo", "Right", "Foot", "Lower", null, null),
          PhysicalMark("Mark", null, "Ear", null, "Some comment", null),
          PhysicalMark("Other", "Centre", "Arm", null, null, null),
          PhysicalMark("Scar", null, "Torso", null, null, null),
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    assertThat(prisoner.tattoos).containsExactly(BodyPartDetail("Elbow", "Comment here"), BodyPartDetail("Foot", null))
    assertThat(prisoner.marks).containsExactly(BodyPartDetail("Ear", "Some comment"))
    assertThat(prisoner.scars).containsExactly(BodyPartDetail("Torso", null))
    assertThat(prisoner.otherMarks).containsExactly(BodyPartDetail("Arm", null))
  }
}

private fun aBooking() = OffenderBooking("A1234AA", "Fred", "Bloggs", LocalDate.now().minusYears(18), false)
