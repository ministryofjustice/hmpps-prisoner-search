package uk.gov.justice.digital.hmpps.prisonersearch.indexer.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Address
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.BodyPartDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Identifier
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Language
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Offence
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PersonalCareNeed
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.AlertCodeSummary
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.EmailAddress
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenceHistoryDetail
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderIdentifier
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.OffenderLanguageDto
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.PersonalCareNeedDto
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.PhysicalAttributes
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.PhysicalCharacteristic
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.PhysicalMark
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.SentenceDetail
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.Telephone
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.nomis.Address as NomisAddress

class TranslatorTest {

  @Test
  fun `when prisoner has no booking associated the booking information is missing`() {
    val dateOfBirth = LocalDate.now().minusYears(18)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        firstName = "Fred",
        lastName = "Bloggs",
        dateOfBirth = dateOfBirth,
        title = "Mr",
      ),
    )

    assertThat(prisoner.prisonerNumber).isEqualTo("A1234AA")
    assertThat(prisoner.firstName).isEqualTo("Fred")
    assertThat(prisoner.lastName).isEqualTo("Bloggs")
    assertThat(prisoner.dateOfBirth).isEqualTo(dateOfBirth)
    assertThat(prisoner.title).isEqualTo("Mr")
    assertThat(prisoner.bookingId).isNull()
  }

  @Test
  fun `topupSupervisionExpiryDate is present`() {
    val tseDate = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        sentenceDetail = SentenceDetail(topupSupervisionExpiryDate = tseDate),
      ),
    )
    assertThat(prisoner.topupSupervisionExpiryDate).isEqualTo(tseDate)
  }

  @Test
  fun `topupSupervisionStartDate is present`() {
    val tssDate = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        sentenceDetail = SentenceDetail(topupSupervisionStartDate = tssDate),
      ),
    )
    assertThat(prisoner.topupSupervisionStartDate).isEqualTo(tssDate)
  }

  @Test
  fun `homeDetentionCurfewEndDate is present`() {
    val hdcend = LocalDate.of(2021, 5, 15)
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        sentenceDetail = SentenceDetail(homeDetentionCurfewEndDate = hdcend),
      ),
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
        offenderNo = "A1234AA",
        offenderId = 1L,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = releaseDate,
          conditionalReleaseOverrideDate = conditionalReleaseOverrideDate,
          automaticReleaseDate = releaseDate,
          automaticReleaseOverrideDate = automaticReleaseOverrideDate,
          postRecallReleaseDate = releaseDate,
          postRecallReleaseOverrideDate = postRecallReleaseOverrideDate,
        ),
      ),
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
        offenderNo = "A1234AA",
        offenderId = 1L,
        sentenceDetail = SentenceDetail(
          conditionalReleaseDate = conditionalReleaseDate,
          automaticReleaseDate = automaticReleaseDate,
          postRecallReleaseDate = postRecallReleaseDate,
        ),
      ),
    )
    assertThat(prisoner.conditionalReleaseDate).isEqualTo(conditionalReleaseDate)
    assertThat(prisoner.automaticReleaseDate).isEqualTo(automaticReleaseDate)
    assertThat(prisoner.postRecallReleaseDate).isEqualTo(postRecallReleaseDate)
  }

  @Test
  fun `imprisonmentStatus and description and convictedStatus are present`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        imprisonmentStatus = "LIFE",
        imprisonmentStatusDescription = "Serving Life Imprisonment",
        convictedStatus = "Remand",
      ),
    )
    assertThat(prisoner.imprisonmentStatus).isEqualTo("LIFE")
    assertThat(prisoner.imprisonmentStatusDescription).isEqualTo("Serving Life Imprisonment")
    assertThat(prisoner.convictedStatus).isEqualTo("Remand")
  }

  @Test
  fun `reception and admission dates are present`() {
    val prisoner = Prisoner().translate(
      ob = OffenderBooking(
        offenderNo = "A1234AA",
        offenderId = 1L,
        receptionDate = LocalDate.parse("2025-04-21"),
        lastAdmissionTime = LocalDateTime.parse("2025-04-23T15:20:26"),
      ),
    )
    assertThat(prisoner.receptionDate).isEqualTo("2025-04-21")
    assertThat(prisoner.lastAdmissionDate).isEqualTo("2025-04-23")
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
      incentiveLevel = Result.success(
        IncentiveLevel(
          iepCode = "STD",
          iepLevel = "Standard",
          iepTime = LocalDateTime.parse("2021-01-01T11:00:00"),
          nextReviewDate = LocalDate.parse("2022-02-02"),
        ),
      ),
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
  internal fun `alerts data is mapped`() {
    fun alert(code: String, activeTo: LocalDate?): Alert {
      val alert = Alert(
        alertUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00"),
        prisonNumber = "A1234AA",
        alertCode = AlertCodeSummary("x-type", "type-description", code, "x-description"),
        description = null,
        authorisedBy = null,
        activeFrom = LocalDate.parse("2021-09-27"),
        activeTo = activeTo,
        isActive = false,
        createdAt = LocalDateTime.parse("2021-09-27T14:19:25"),
        createdBy = "USER1234",
        createdByDisplayName = "Firstname Lastname",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        activeToLastSetAt = null,
        activeToLastSetBy = null,
        activeToLastSetByDisplayName = null,
      )
      return alert
    }

    val prisoner = Prisoner().translate(
      ob = aBooking(),
      alerts = Result.success(
        listOf(
          alert("X0", LocalDate.now()),
          alert("X1", activeTo = LocalDate.now().plusDays(1)),
          alert("X2", activeTo = null),
        ),
      ),
    )

    assertThat(prisoner.alerts).isNotNull
    assertThat(prisoner.alerts).hasSize(3)
    assertThat(prisoner.alerts?.get(0)).isEqualTo(
      PrisonerAlert(
        alertCode = "X0",
        alertType = "x-type",
        expired = true,
        active = false,
      ),
    )
    assertThat(prisoner.alerts?.get(1)).isEqualTo(
      PrisonerAlert(
        alertCode = "X1",
        alertType = "x-type",
        expired = false,
        active = false,
      ),
    )
    assertThat(prisoner.alerts?.get(2)).isEqualTo(
      PrisonerAlert(
        alertCode = "X2",
        alertType = "x-type",
        expired = false,
        active = false,
      ),
    )
  }

  @Test
  fun `alerts data is unchanged on failure`() {
    val alerts = listOf(
      PrisonerAlert(
        alertCode = "X0",
        alertType = "x-type",
        expired = true,
        active = false,
      ),
    )
    val existingPrisoner = Prisoner()
      .apply {
        this.alerts = alerts
      }
    val newPrisoner = Prisoner()
      .translate(
        existingPrisoner,
        ob = aBooking(),
        alerts = Result.failure(RuntimeException("It has gone badly wrong")),
      )
    assertThat(newPrisoner.alerts).isSameAs(alerts)
  }

  @Test
  fun `should map last prison ID`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(latestLocationId = "LEI"),
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
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
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
      )

      assertThat(prisoner.currentIncentive).isNull()
    }

    @Test
    internal fun `will fall back to null when the previous record's incentive was null`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        incentiveLevel = Result.success(null),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        Result.failure(RuntimeException("It has gone badly wrong")),
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
    internal fun `will fall back to booking locationDescription on failure or not an RP event`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking(),
        restrictedPatientData = Result.failure(Exception()),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking().copy(locationDescription = "OUT"),
        restrictedPatientData = Result.failure(RuntimeException("It has gone badly wrong")),
      )

      assertThat(prisoner.locationDescription).isEqualTo("OUT")
    }

    @Test
    internal fun `will fall back to null when previous record did not exist`() {
      val prisoner = Prisoner().translate(
        existingPrisoner = null,
        ob = aBooking().copy(locationDescription = "previous location"),
        restrictedPatientData = Result.failure(RuntimeException("It has gone badly wrong")),
      )

      assertThat(prisoner.restrictedPatient).isFalse()
      assertThat(prisoner.locationDescription).isEqualTo("previous location")
      assertThat(prisoner.supportingPrisonId).isNull()
    }

    @Test
    internal fun `will fall back to null when the previous record's restricted patient data was null`() {
      val existingPrisoner = Prisoner().translate(
        ob = aBooking().copy(locationDescription = "OUT"),
        restrictedPatientData = Result.success(null),
      )

      val prisoner = Prisoner().translate(
        existingPrisoner,
        aBooking(),
        restrictedPatientData = Result.failure(RuntimeException("It has gone badly wrong")),
      )

      assertThat(prisoner.restrictedPatient).isFalse()
      assertThat(prisoner.dischargedHospitalId).isNull()
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
    )
    assertThat(prisoner.tattoos)
      .containsExactly(BodyPartDetail("Elbow", "Comment here"), BodyPartDetail("Foot", null))
    assertThat(prisoner.marks)
      .containsExactly(BodyPartDetail("Ear", "Some comment"), BodyPartDetail("Arm", null))
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

  @Test
  fun `should map email addresses`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        emailAddresses = listOf(EmailAddress("personalemail@hotmail.com"), EmailAddress("backupemail@gmail.com")),
      ),
    )

    assertThat(prisoner.emailAddresses).extracting("email")
      .containsExactly("personalemail@hotmail.com", "backupemail@gmail.com")
  }

  @Test
  fun `should map telephone numbers`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        phones = listOf(
          Telephone("0114 1234567", "HOME"),
          Telephone("0777 1234567", "MOB"),
          Telephone("0114 7654321", "OTH"),
        ),
      ),
    )

    assertThat(prisoner.phoneNumbers).extracting("type", "number")
      .containsExactlyInAnyOrder(
        Tuple.tuple("HOME", "01141234567"),
        Tuple.tuple("MOB", "07771234567"),
      )
  }

  @Nested
  inner class PersonalCareNeeds {
    @Test
    internal fun `Active PersonalCareNeeds are mapped`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          personalCareNeeds = listOf(
            PersonalCareNeedDto(
              problemType = "TYPE1",
              problemCode = "CODE1",
              problemStatus = "STATUS1",
              problemDescription = "Desc1",
              commentText = "Comment1",
              startDate = LocalDate.parse("2023-04-05"),
              endDate = null,
            ),
            PersonalCareNeedDto(
              problemType = "INACTIVE",
              problemCode = "CODE2",
              problemStatus = "STATUS2",
              problemDescription = "Desc2",
              commentText = "Comment2",
              startDate = LocalDate.parse("2023-04-05"),
              endDate = LocalDate.parse("2025-04-05"),
            ),
            PersonalCareNeedDto(
              problemType = "TYPE3",
              problemCode = "CODE3",
              problemStatus = "STATUS3",
              problemDescription = "Desc3",
              commentText = "Comment3",
              startDate = LocalDate.parse("2023-04-05"),
              endDate = LocalDate.parse("2199-04-05"),
            ),
          ),
        ),
      )
      assertThat(prisoner.personalCareNeeds)
        .containsExactlyInAnyOrder(
          PersonalCareNeed(
            problemType = "TYPE1",
            problemCode = "CODE1",
            problemStatus = "STATUS1",
            problemDescription = "Desc1",
            commentText = "Comment1",
            startDate = LocalDate.parse("2023-04-05"),
            endDate = null,
          ),
          PersonalCareNeed(
            problemType = "TYPE3",
            problemCode = "CODE3",
            problemStatus = "STATUS3",
            problemDescription = "Desc3",
            commentText = "Comment3",
            startDate = LocalDate.parse("2023-04-05"),
            endDate = LocalDate.parse("2199-04-05"),
          ),
        )
    }
  }

  @Nested
  inner class Languages {
    @Test
    internal fun `Languages are mapped`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          languages = listOf(
            OffenderLanguageDto(
              type = "TYPE",
              code = "ENG",
              readSkill = "G",
              writeSkill = "A",
              speakSkill = "P",
              interpreterRequested = true,
            ),
          ),
        ),
      )
      assertThat(prisoner.languages)
        .containsExactlyInAnyOrder(
          Language(
            type = "TYPE",
            code = "ENG",
            readSkill = "G",
            writeSkill = "A",
            speakSkill = "P",
            interpreterRequested = true,
          ),
        )
    }
  }

  @Nested
  inner class Addresses {
    @Test
    fun `should map all address fields`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address(
          fullAddress = "Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          postalCode = "S10 1AB",
          startDate = LocalDate.now(),
          primaryAddress = true,
          noFixedAddress = false,
        ),
      )
    }

    @Test
    fun `should handle missing flat`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address(
          "3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          "S10 1AB",
          LocalDate.now(),
          true,
          false,
        ),
      )
    }

    @Test
    fun `should handle multiple addresses`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address(
          "Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
          "S10 1AB",
          LocalDate.now(),
          true,
          false,
        ),
        Address("1 Big Street, Sheffield, S11 1BB", "S11 1BB", LocalDate.now(), false, false),
      )
    }

    @Test
    fun `should handle different combinations of premise and street`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
            NomisAddress(
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
              null,
            ),
            NomisAddress(
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
              null,
            ),
            NomisAddress(
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
              null,
            ),
          ),
        ),
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
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("S11 1BB", "S11 1BB", LocalDate.now(), false, false),
      )
    }

    @Test
    fun `should handle missing postal code`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, England", null, LocalDate.now(), true, false),
      )
    }

    @Test
    fun `should handle missing start date`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address("Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England", "S10 1AB", null, true, false),
      )
    }

    @Test
    fun `should map telephone numbers`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              listOf(
                Telephone("0114 1234567", "HOME"),
                Telephone("0777 1234567", "MOB"),
                Telephone("0114 7654321", "OTH"),
              ),
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses!![0].phoneNumbers).extracting("type", "number")
        .containsExactlyInAnyOrder(
          Tuple.tuple("HOME", "01141234567"),
          Tuple.tuple("MOB", "07771234567"),
        )
    }

    @Test
    fun `should map no fixed address`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          addresses = listOf(
            NomisAddress(
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
              null,
              noFixedAddress = true,
            ),
          ),
        ),
      )

      assertThat(prisoner.addresses).containsExactly(
        Address(
          fullAddress = "No fixed address",
          postalCode = null,
          startDate = LocalDate.now(),
          primaryAddress = true,
          noFixedAddress = true,
        ),
      )
    }
  }

  @Test
  fun `should map all convicted offences`() {
    val prisoner = Prisoner().translate(
      ob = aBooking().copy(
        bookingId = 2,
        allConvictedOffences = listOf(
          OffenceHistoryDetail(
            bookingId = 1,
            offenceDate = LocalDate.now().minusYears(1),
            offenceRangeDate = null,
            offenceDescription = "Robbery",
            statuteCode = "TH68",
            offenceCode = "TH68023",
            mostSerious = true,
            offenceSeverityRanking = 100,
            sentenceStartDate = null,
            primarySentence = null,
          ),
          OffenceHistoryDetail(
            bookingId = 2,
            offenceDate = LocalDate.now(),
            offenceRangeDate = null,
            offenceDescription = "Burglary other than dwelling - theft",
            statuteCode = "TH68",
            offenceCode = "TH68037",
            mostSerious = true,
            offenceSeverityRanking = 90,
            sentenceStartDate = LocalDate.parse("2017-02-03"),
            primarySentence = true,
          ),
        ),
      ),
    )

    assertThat(prisoner.allConvictedOffences)
      .containsExactlyInAnyOrder(
        Offence("TH68", "TH68023", "Robbery", LocalDate.now().minusYears(1), false, null, null),
        Offence("TH68", "TH68037", "Burglary other than dwelling - theft", LocalDate.now(), true, LocalDate.parse("2017-02-03"), true),
      )
  }

  @Nested
  inner class Identifiers {
    @ParameterizedTest
    @CsvSource(
      "12/394773H,12/394773H",
      "12/0394773H,12/394773H",
      "2012/394773H,12/394773H",
      "2012/0394773H,12/394773H",
      "INVALID_PNC,INVALID_PNC",
    )
    fun `should always convert PNC number to short format if possible`(nomisPnc: String, prisonerPnc: String) {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          allIdentifiers = listOf(OffenderIdentifier(1L, "PNC", nomisPnc, null, null, LocalDateTime.now())),
        ),
      )

      assertThat(prisoner.identifiers?.first()?.value).isEqualTo(prisonerPnc)
    }

    @Test
    fun `MERGED identifiers are mapped`() {
      val aTimestamp = LocalDateTime.parse("2025-06-14T12:13:14")
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          allIdentifiers = listOf(
            OffenderIdentifier(1L, "MERGED", "B1234BB", null, null, aTimestamp),
          ),
        ),
      )
      with(prisoner.identifiers?.first()!!) {
        assertThat(type).isEqualTo("MERGED")
        assertThat(value).isEqualTo("B1234BB")
        assertThat(createdDateTime).isEqualTo(aTimestamp)
      }
    }

    @Test
    fun `should map identifiers`() {
      val prisoner = Prisoner().translate(
        ob = aBooking().copy(
          allIdentifiers = listOf(
            OffenderIdentifier(offenderId = 123, type = "PNC", value = "2012/0394773H", issuedDate = LocalDate.parse("2019-07-17"), issuedAuthorityText = "NOMIS", whenCreated = LocalDateTime.parse("2019-07-17T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "PNC", value = "12/0394773H", issuedDate = LocalDate.parse("2019-07-17"), issuedAuthorityText = null, whenCreated = LocalDateTime.parse("2020-07-17T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "CRO", value = "145845/12U", issuedDate = null, issuedAuthorityText = "Incorrect CRO - typo", whenCreated = LocalDateTime.parse("2021-10-18T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "CRO", value = "145835/12U", issuedDate = null, issuedAuthorityText = null, whenCreated = LocalDateTime.parse("2021-10-19T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "NINO", value = "JE460605B", issuedDate = null, issuedAuthorityText = null, whenCreated = LocalDateTime.parse("2019-06-11T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "DL", value = "COLBO/912052/JM9MU", issuedDate = null, issuedAuthorityText = null, whenCreated = LocalDateTime.parse("2022-04-12T12:34:56.833133")),
            OffenderIdentifier(offenderId = 123, type = "HOREF", value = "T3037620", issuedDate = null, issuedAuthorityText = null, whenCreated = LocalDateTime.parse("2020-04-12T12:34:56.833133")),
          ),
        ),
      )
      assertThat(prisoner.identifiers)
        .containsExactly(
          Identifier("NINO", "JE460605B", null, null, LocalDateTime.parse("2019-06-11T12:34:56")),
          Identifier("PNC", "12/394773H", LocalDate.parse("2019-07-17"), "NOMIS", LocalDateTime.parse("2019-07-17T12:34:56")),
          Identifier("PNC", "12/394773H", LocalDate.parse("2019-07-17"), null, LocalDateTime.parse("2020-07-17T12:34:56")),
          Identifier("CRO", "145845/12U", null, "Incorrect CRO - typo", LocalDateTime.parse("2021-10-18T12:34:56")),
          Identifier("CRO", "145835/12U", null, null, LocalDateTime.parse("2021-10-19T12:34:56")),
          Identifier("DL", "COLBO/912052/JM9MU", null, null, LocalDateTime.parse("2022-04-12T12:34:56")),
        )
      assertThat(prisoner.pncNumber).isEqualTo("12/0394773H")
      assertThat(prisoner.pncNumberCanonicalShort).isEqualTo("12/394773H")
      assertThat(prisoner.pncNumberCanonicalLong).isEqualTo("2012/394773H")
      assertThat(prisoner.croNumber).isEqualTo("145835/12U")
    }
  }
}

private fun aBooking() = OffenderBooking("A1234AA", 1L, "Fred", "Bloggs", LocalDate.now().minusYears(18))
