package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Address
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
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.Address as NomisAddress

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {
    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking("A1234AA", "Fred", "Bloggs", dateOfBirth),
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
    assertThat(prisoner.marks).containsExactly(BodyPartDetail("Ear", "Some comment"), BodyPartDetail("Arm", null))
    assertThat(prisoner.scars).containsExactly(BodyPartDetail("Torso", null))
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
    assertThat(prisoner.tattoos).containsExactlyInAnyOrder(
      BodyPartDetail("Elbow", "Comment here"),
      BodyPartDetail("Arm", "Mark tattoo"),
      BodyPartDetail("Head", "Other mark TATTOO"),
    )
    assertThat(prisoner.scars).containsExactlyInAnyOrder(
      BodyPartDetail("Torso", null),
      BodyPartDetail("Shoulder", "Mark scar"),
      BodyPartDetail("Hand", "Other mark SCAR"),
    )
    assertThat(prisoner.marks).containsExactlyInAnyOrder(
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
            NomisAddress(1, "2", "3", "Main Street", "Crookes", "Sheffield", "S10 1AB", "South Yorkshire", "England", true, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", LocalDate.now(), true),
      )
    }

    @Test
    fun `should handle missing flat`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, null, "3", "Main Street", "Crookes", "Sheffield", "S10 1AB", "South Yorkshire", "England", true, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", LocalDate.now(), true),
      )
    }

    @Test
    fun `should handle multiple addresses`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, "2", "3", "Main Street", "Crookes", "Sheffield", "S10 1AB", "South Yorkshire", "England", true, LocalDate.now()),
            NomisAddress(2, null, "1", "Big Street", null, "Sheffield", "S11 1BB", null, null, false, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", LocalDate.now(), true),
        Address("1 Big Street, Sheffield, S11 1BB", "S11 1BB", LocalDate.now(), false),
      )
    }

    @Test
    fun `should handle different combinations of premise and street`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, null, "1", "Main Street", "locality", "any", "any", "any", "any", true, LocalDate.now()),
            NomisAddress(2, null, "Big House", "Main Street", "locality", "any", "any", "any", "any", true, LocalDate.now()),
            NomisAddress(3, null, "Big House", null, "locality", "any", "any", "any", "any", true, LocalDate.now()),
            NomisAddress(4, null, null, "Main Street", "locality", "any", "any", "any", "any", true, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses!![0].fullAddress).startsWith("1 Main Street, locality, ")
      assertThat(prisoner.addresses!![1].fullAddress).startsWith("Big House, Main Street, locality, ")
      assertThat(prisoner.addresses!![2].fullAddress).startsWith("Big House, locality, ")
      assertThat(prisoner.addresses!![3].fullAddress).startsWith("Main Street, locality, ")
    }

    @Test
    fun `should handle missing address fields`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, null, null, null, null, null, "S11 1BB", null, null, false, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("S11 1BB", "S11 1BB", LocalDate.now(), false),
      )
    }

    @Test
    fun `should handle missing postal code`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, "2", "3", "Main Street", "Crookes", "Sheffield", null, "South Yorkshire", "England", true, LocalDate.now()),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, England", null, LocalDate.now(), true),
      )
    }

    @Test
    fun `should handle missing start date`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(1, "2", "3", "Main Street", "Crookes", "Sheffield", "S10 1AB", "South Yorkshire", "England", true, null),
          ),
        ),
        incentiveLevel = Result.success(null),
        restrictedPatientData = Result.success(null),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", null, true),
      )
    }
  }
}

private fun aBooking() = OffenderBooking("A1234AA", "Fred", "Bloggs", LocalDate.now().minusYears(18))
