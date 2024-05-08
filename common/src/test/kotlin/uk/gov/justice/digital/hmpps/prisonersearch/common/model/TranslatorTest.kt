package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.SentenceDetail
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {
    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    Assertions.assertThat(prisoner.prisonerNumber).isEqualTo("A1234AA")
    Assertions.assertThat(prisoner.firstName).isEqualTo("Fred")
    Assertions.assertThat(prisoner.lastName).isEqualTo("Bloggs")
    Assertions.assertThat(prisoner.dateOfBirth).isEqualTo(dateOfBirth)
    Assertions.assertThat(prisoner.bookingId).isNull()
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
        sentenceDetail = SentenceDetail(topupSupervisionExpiryDate = tseDate),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.topupSupervisionExpiryDate).isEqualTo(tseDate)
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
        sentenceDetail = SentenceDetail(topupSupervisionStartDate = tssDate),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.topupSupervisionStartDate).isEqualTo(tssDate)
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
        sentenceDetail = SentenceDetail(homeDetentionCurfewEndDate = hdcend),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.homeDetentionCurfewEndDate).isEqualTo(hdcend)
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
    Assertions.assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseOverrideDate)
    Assertions.assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseOverrideDate)
    Assertions.assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseOverrideDate)
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
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = conditionalReleaseDate,
          automaticReleaseDate = automaticReleaseDate,
          postRecallReleaseDate = postRecallReleaseDate,
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseDate)
    Assertions.assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseDate)
    Assertions.assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseDate)
  }

  @Test
  fun `imprisonmentStatus and description are present`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
        imprisonmentStatus = "LIFE",
        imprisonmentStatusDescription = "Serving Life Imprisonment",
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.imprisonmentStatus).isEqualTo("LIFE")
    Assertions.assertThat(prisoner.imprisonmentStatusDescription).isEqualTo("Serving Life Imprisonment")
  }

  @Test
  fun `maps alerts correctly`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        "A1234AA",
        "Fred",
        "Bloggs",
        LocalDate.of(1976, 5, 15),
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

    Assertions.assertThat(prisoner.alerts?.first())
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

    Assertions.assertThat(prisoner.currentIncentive).isNotNull
    Assertions.assertThat(prisoner.currentIncentive?.level).isNotNull
    Assertions.assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
    Assertions.assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    Assertions.assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    Assertions.assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
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

    Assertions.assertThat(prisoner.currentIncentive).isNotNull
    Assertions.assertThat(prisoner.currentIncentive?.level).isNotNull
    Assertions.assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
    Assertions.assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
    Assertions.assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
    Assertions.assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
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

    Assertions.assertThat(prisoner.restrictedPatient).isTrue
    Assertions.assertThat(prisoner.supportingPrisonId).isEqualTo("MDI")
    Assertions.assertThat(prisoner.dischargedHospitalId).isEqualTo("HAZLWD")
    Assertions.assertThat(prisoner.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
    Assertions.assertThat(prisoner.dischargeDate).isEqualTo(LocalDate.now())
    Assertions.assertThat(prisoner.dischargeDetails).isEqualTo("Getting worse")
    Assertions.assertThat(prisoner.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
  }

  @Test
  internal fun `restricted patient data can be null`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(locationDescription = "OUT"),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    Assertions.assertThat(prisoner.restrictedPatient).isFalse
    Assertions.assertThat(prisoner.supportingPrisonId).isNull()
    Assertions.assertThat(prisoner.dischargedHospitalId).isNull()
    Assertions.assertThat(prisoner.dischargedHospitalDescription).isNull()
    Assertions.assertThat(prisoner.dischargeDate).isNull()
    Assertions.assertThat(prisoner.dischargeDetails).isNull()
    Assertions.assertThat(prisoner.locationDescription).isEqualTo("OUT")
  }

  @Test
  fun `should map last prison ID`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(latestLocationId = "LEI"),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )

    Assertions.assertThat(prisoner.lastPrisonId).isEqualTo("LEI")
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

      Assertions.assertThat(prisoner.currentIncentive).isNotNull
      Assertions.assertThat(prisoner.currentIncentive?.level).isNotNull
      Assertions.assertThat(prisoner.currentIncentive?.level?.code).isEqualTo("STD")
      Assertions.assertThat(prisoner.currentIncentive?.level?.description).isEqualTo("Standard")
      Assertions.assertThat(prisoner.currentIncentive?.dateTime).isEqualTo(LocalDateTime.parse("2021-01-01T11:00:00"))
      Assertions.assertThat(prisoner.currentIncentive?.nextReviewDate).isEqualTo(LocalDate.parse("2022-02-02"))
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.currentIncentive).isNull()
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

      Assertions.assertThat(prisoner.currentIncentive).isNull()
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

      Assertions.assertThat(prisoner.locationDescription).isEqualTo("OUT - discharged to Hazelwood Hospital")
      Assertions.assertThat(prisoner.restrictedPatient).isTrue
      Assertions.assertThat(prisoner.supportingPrisonId).isEqualTo("MDI")
      Assertions.assertThat(prisoner.dischargedHospitalId).isEqualTo("HAZLWD")
      Assertions.assertThat(prisoner.dischargedHospitalDescription).isEqualTo("Hazelwood Hospital")
      Assertions.assertThat(prisoner.dischargeDate).isEqualTo(LocalDate.now())
      Assertions.assertThat(prisoner.dischargeDetails).isEqualTo("Getting worse")
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = aBooking().copy(locationDescription = "previous location"),
        Result.failure(RuntimeException("It has gone badly wrong")),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.restrictedPatient).isFalse()
      Assertions.assertThat(prisoner.locationDescription).isEqualTo("previous location")
      Assertions.assertThat(prisoner.supportingPrisonId).isNull()
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

      Assertions.assertThat(prisoner.restrictedPatient).isFalse()
      Assertions.assertThat(prisoner.locationDescription).isEqualTo("OUT")
      Assertions.assertThat(prisoner.supportingPrisonId).isNull()
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
    Assertions.assertThat(prisoner.gender).isEqualTo("M")
    Assertions.assertThat(prisoner.ethnicity).isEqualTo("W")
    Assertions.assertThat(prisoner.heightCentimetres).isEqualTo(200)
    Assertions.assertThat(prisoner.weightKilograms).isEqualTo(100)
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
    Assertions.assertThat(prisoner.hairColour).isEqualTo("Red")
    Assertions.assertThat(prisoner.rightEyeColour).isEqualTo("Green")
    Assertions.assertThat(prisoner.leftEyeColour).isEqualTo("Hazel")
    Assertions.assertThat(prisoner.facialHair).isEqualTo("Clean Shaven")
    Assertions.assertThat(prisoner.shapeOfFace).isEqualTo("Bullet")
    Assertions.assertThat(prisoner.build).isEqualTo("Proportional")
    Assertions.assertThat(prisoner.shoeSize).isEqualTo(10)
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
    Assertions.assertThat(prisoner.tattoos)
      .containsExactly(BodyPartDetail("Elbow", "Comment here"), BodyPartDetail("Foot", null))
    Assertions.assertThat(prisoner.marks)
      .containsExactly(BodyPartDetail("Ear", "Some comment"), BodyPartDetail("Arm", null))
    Assertions.assertThat(prisoner.scars).containsExactly(BodyPartDetail("Torso", null))
  }

  @Test
  internal fun `Physical Marks adds tattoos and scars from marks or other marks`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        physicalMarks = listOf(
          PhysicalMark("Tattoo", "Left", "Elbow", "Upper", "Comment here", null),
          PhysicalMark("Scar", null, "Torso", null, null, null),
          PhysicalMark("Mark", null, "Arm", null, "Mark tattoo", null),
          PhysicalMark("Mark", null, "Shoulder", null, "Mark scar", null),
          PhysicalMark("Other", "Centre", "Head", null, "Other mark TATTOO", null),
          PhysicalMark("Other", "Centre", "Hand", null, "Other mark SCAR", null),
        ),
      ),
      incentiveLevel = Result.success(null),
      restrictedPatientData = Result.success(null),
    )
    Assertions.assertThat(prisoner.tattoos).containsExactlyInAnyOrder(
      BodyPartDetail("Elbow", "Comment here"),
      BodyPartDetail("Arm", "Mark tattoo"),
      BodyPartDetail("Head", "Other mark TATTOO"),
    )
    Assertions.assertThat(prisoner.scars).containsExactlyInAnyOrder(
      BodyPartDetail("Torso", null),
      BodyPartDetail("Shoulder", "Mark scar"),
      BodyPartDetail("Hand", "Other mark SCAR"),
    )
    Assertions.assertThat(prisoner.marks).containsExactlyInAnyOrder(
      BodyPartDetail("Arm", "Mark tattoo"),
      BodyPartDetail("Head", "Other mark TATTOO"),
      BodyPartDetail("Shoulder", "Mark scar"),
      BodyPartDetail("Hand", "Other mark SCAR"),
    )
  }

  @Nested
  inner class Addresses {
    @Test
    fun `should map all address fields`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              "2",
              "3",
              "Main Street",
              "Crookes",
              "Sheffield",
              "S10 1AB",
              "South Yorkshire",
              "England",
              true,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address(
          "Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          "S10 1AB",
          LocalDate.now(),
          true,
        ),
      )
    }

    @Test
    fun `should handle missing flat`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              null,
              "3",
              "Main Street",
              "Crookes",
              "Sheffield",
              "S10 1AB",
              "South Yorkshire",
              "England",
              true,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address(
          "3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          "S10 1AB",
          LocalDate.now(),
          true,
        ),
      )
    }

    @Test
    fun `should handle multiple addresses`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              "2",
              "3",
              "Main Street",
              "Crookes",
              "Sheffield",
              "S10 1AB",
              "South Yorkshire",
              "England",
              true,
              LocalDate.now(),
            ),
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              2,
              null,
              "1",
              "Big Street",
              null,
              "Sheffield",
              "S11 1BB",
              null,
              null,
              false,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address(
          "Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          "S10 1AB",
          LocalDate.now(),
          true,
        ),
        Address("1 Big Street, Sheffield, S11 1BB", "S11 1BB", LocalDate.now(), false),
      )
    }

    @Test
    fun `should handle different combinations of premise and street`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              null,
              "1",
              "Main Street",
              "locality",
              "any",
              "any",
              "any",
              "any",
              true,
              LocalDate.now(),
            ),
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              2,
              null,
              "Big House",
              "Main Street",
              "locality",
              "any",
              "any",
              "any",
              "any",
              true,
              LocalDate.now(),
            ),
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              3,
              null,
              "Big House",
              null,
              "locality",
              "any",
              "any",
              "any",
              "any",
              true,
              LocalDate.now(),
            ),
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              4,
              null,
              null,
              "Main Street",
              "locality",
              "any",
              "any",
              "any",
              "any",
              true,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses!![0].fullAddress).startsWith("1 Main Street, locality, ")
      Assertions.assertThat(prisoner.addresses!![1].fullAddress).startsWith("Big House, Main Street, locality, ")
      Assertions.assertThat(prisoner.addresses!![2].fullAddress).startsWith("Big House, locality, ")
      Assertions.assertThat(prisoner.addresses!![3].fullAddress).startsWith("Main Street, locality, ")
    }

    @Test
    fun `should handle missing address fields`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              null,
              null,
              null,
              null,
              null,
              "S11 1BB",
              null,
              null,
              false,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address("S11 1BB", "S11 1BB", LocalDate.now(), false),
      )
    }

    @Test
    fun `should handle missing postal code`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              "2",
              "3",
              "Main Street",
              "Crookes",
              "Sheffield",
              null,
              "South Yorkshire",
              "England",
              true,
              LocalDate.now(),
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, England", null, LocalDate.now(), true),
      )
    }

    @Test
    fun `should handle missing start date`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.Address(
              1,
              "2",
              "3",
              "Main Street",
              "Crookes",
              "Sheffield",
              "S10 1AB",
              "South Yorkshire",
              "England",
              true,
              null,
            ),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      Assertions.assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", null, true),
      )
    }
  }
}

private fun aBooking() = OffenderBooking("A1234AA", "Fred", "Bloggs", LocalDate.now().minusYears(18))
