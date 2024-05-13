package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Address
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.BodyPartDetail
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.EmailAddress
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlias
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.EQ
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.LT
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl.RequestDsl
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * We have other tests for different field types and for query structure, but we also need to check that every single
 * field on the prisoner record is searchable.
 */
class AttributeSearchFieldsIntegrationTest : AbstractSearchIntegrationTest() {
  val prisoner = Prisoner().apply {
    prisonerNumber = "A1234AA"
    pncNumber = "12/394773H"
    pncNumberCanonicalShort = "12/394773H"
    pncNumberCanonicalLong = "2012/394773H"
    croNumber = "29906/12J"
    bookingId = "0001200924"
    bookNumber = "38412A"
    title = "Mr"
    firstName = "Robert"
    middleNames = "John James"
    lastName = "Larsen"
    gender = "Male"
    ethnicity = "White: Eng./Welsh/Scot./N.Irish/British"
    maritalStatus = "Widowed"
    religion = "Church of England (Anglican)"
    nationality = "Egyptian"
    status = "ACTIVE IN"
    lastMovementTypeCode = "CRT"
    lastMovementReasonCode = "CA"
    inOutStatus = "IN"
    prisonId = "MDI"
    lastPrisonId = "MDI"
    prisonName = "HMP Moorland"
    aliases = listOf(
      PrisonerAlias(
        title = "Sir",
        firstName = "Robert",
        middleNames = "John James",
        lastName = "Larsen",
        gender = "Male",
        ethnicity = "White: Eng./Welsh/Scot./N.Irish/British",
        dateOfBirth = LocalDate.parse("1990-01-02"),
      ),
    )
    alerts = listOf(
      PrisonerAlert(
        alertType = "R",
        alertCode = "RLO",
        active = true,
        expired = false,
      ),
    )
    csra = "HIGH"
    category = "C"
    legalStatus = "SENTENCED"
    imprisonmentStatus = "LIFE"
    imprisonmentStatusDescription = "Serving Life Imprisonment"
    mostSeriousOffence = "Robbery"
    nonDtoReleaseDateType = "ARD"
    locationDescription = "HMP Moorland"
    supportingPrisonId = "MDI"
    dischargedHospitalId = "HAZLWD"
    dischargedHospitalDescription = "Hazelwood House"
    dischargeDetails = "Psychiatric Hospital Discharge to Hazelwood House"
    currentIncentive = CurrentIncentive(
      level = IncentiveLevel(
        code = "STD",
        description = "Standard",
      ),
      dateTime = LocalDateTime.parse("2022-11-10T15:47:24"),
      nextReviewDate = LocalDate.parse("2024-01-21"),
    )
    hairColour = "Blonde"
    rightEyeColour = "Green"
    leftEyeColour = "Hazel"
    facialHair = "Clean Shaven"
    shapeOfFace = "Round"
    build = "Muscular"
    tattoos = listOf(
      BodyPartDetail(
        bodyPart = "Torso",
        comment = "Skull and crossbones",
      ),
    )
    scars = listOf(
      BodyPartDetail(
        bodyPart = "Face",
        comment = "Scar on left cheek",
      ),
    )
    marks = listOf(
      BodyPartDetail(
        bodyPart = "Left Arm",
        comment = "Tribal tattoo",
      ),
    )
    additionalDaysAwarded = 10
    heightCentimetres = 180
    weightKilograms = 88
    shoeSize = 10
    youthOffender = true
    indeterminateSentence = true
    restrictedPatient = true
    recall = true
    dateOfBirth = LocalDate.parse("1990-01-01")
    sentenceStartDate = LocalDate.parse("2024-01-01")
    releaseDate = LocalDate.parse("2024-01-02")
    confirmedReleaseDate = LocalDate.parse("2024-01-03")
    licenceExpiryDate = LocalDate.parse("2024-01-05")
    homeDetentionCurfewEligibilityDate = LocalDate.parse("2024-01-06")
    homeDetentionCurfewActualDate = LocalDate.parse("2024-01-07")
    homeDetentionCurfewEndDate = LocalDate.parse("2024-01-08")
    topupSupervisionStartDate = LocalDate.parse("2024-01-09")
    topupSupervisionExpiryDate = LocalDate.parse("2024-01-10")
    nonDtoReleaseDate = LocalDate.parse("2024-01-11")
    receptionDate = LocalDate.parse("2024-01-12")
    paroleEligibilityDate = LocalDate.parse("2024-01-13")
    automaticReleaseDate = LocalDate.parse("2024-01-14")
    postRecallReleaseDate = LocalDate.parse("2024-01-15")
    conditionalReleaseDate = LocalDate.parse("2024-01-16")
    actualParoleDate = LocalDate.parse("2024-01-17")
    tariffDate = LocalDate.parse("2024-01-18")
    releaseOnTemporaryLicenceDate = LocalDate.parse("2024-01-19")
    dischargeDate = LocalDate.parse("2024-01-20")
    addresses = listOf(Address("1 Full Address, Sheffield, S10 1BP", "S10 1BP", LocalDate.parse("2024-01-22"), true))
    emailAddresses = listOf(EmailAddress("robert@gmail.com"))
  }

  override fun loadPrisonerData() {
    loadPrisoners(listOf(prisoner))
  }

  @ParameterizedTest
  @CsvSource(
    "prisonerNumber,A1234AA",
    "pncNumber,12/394773H",
    "pncNumberCanonicalShort,12/394773H",
    "pncNumberCanonicalLong,2012/394773H",
    "croNumber,29906/12J",
    "bookingId,0001200924",
    "bookNumber,38412A",
    "title,Mr",
    "firstName,Robert",
    "middleNames,John James",
    "lastName,Larsen",
    "gender,Male",
    "ethnicity,White: Eng./Welsh/Scot./N.Irish/British",
    "maritalStatus,Widowed",
    "religion,Church of England (Anglican)",
    "nationality,Egyptian",
    "status,ACTIVE IN",
    "lastMovementTypeCode,CRT",
    "lastMovementReasonCode,CA",
    "inOutStatus,IN",
    "prisonId,MDI",
    "lastPrisonId,MDI",
    "prisonName,HMP Moorland",
    "aliases.title,Sir",
    "aliases.firstName,Robert",
    "aliases.middleNames,John James",
    "aliases.lastName,Larsen",
    "aliases.gender,Male",
    "aliases.ethnicity,White: Eng./Welsh/Scot./N.Irish/British",
    "alerts.alertType,R",
    "alerts.alertCode,RLO",
    "csra,HIGH",
    "category,C",
    "legalStatus,SENTENCED",
    "imprisonmentStatus,LIFE",
    "imprisonmentStatusDescription,Serving Life Imprisonment",
    "mostSeriousOffence,Robbery",
    "nonDtoReleaseDateType,ARD",
    "locationDescription,HMP Moorland",
    "supportingPrisonId,MDI",
    "dischargedHospitalId,HAZLWD",
    "dischargedHospitalDescription,Hazelwood House",
    "dischargeDetails,Psychiatric Hospital Discharge to Hazelwood House",
    "currentIncentive.level.code,STD",
    "currentIncentive.level.description,Standard",
    "hairColour,Blonde",
    "rightEyeColour,Green",
    "leftEyeColour,Hazel",
    "facialHair,Clean Shaven",
    "shapeOfFace,Round",
    "build,Muscular",
    "tattoos.bodyPart,Torso",
    "tattoos.comment,Skull and crossbones",
    "scars.bodyPart,Face",
    "scars.comment,Scar on left cheek",
    "marks.bodyPart,Left Arm",
    "marks.comment,Tribal tattoo",
    "addresses.fullAddress,'1 Full Address, Sheffield, S10 1BP'",
    "addresses.postalCode,S10 1BP",
    "emailAddresses.email,robert@gmail.com",
  )
  fun `string fields`(field: String, value: String) {
    val request = RequestDsl {
      query {
        stringMatcher(field IS value)
      }
    }

    webTestClient.attributeSearch(request).expectSingleResult(field, value)
  }

  @ParameterizedTest
  @CsvSource(
    "additionalDaysAwarded,10",
    "heightCentimetres,180",
    "weightKilograms,88",
    "shoeSize,10",
  )
  fun `int fields`(field: String, value: Int) {
    val request = RequestDsl {
      query {
        intMatcher(field EQ value)
      }
    }

    webTestClient.attributeSearch(request).expectSingleResult(field, value)
  }

  @ParameterizedTest
  @CsvSource(
    "youthOffender,true",
    "recall,true",
    "indeterminateSentence,true",
    "restrictedPatient,true",
    "alerts.active,true",
    "alerts.expired,false",
    "addresses.primaryAddress,true",
  )
  fun `boolean fields`(field: String, value: Boolean) {
    val request = RequestDsl {
      query {
        booleanMatcher(field IS value)
      }
    }

    webTestClient.attributeSearch(request).expectSingleResult(field, value)
  }

  @ParameterizedTest
  @CsvSource(
    "dateOfBirth,1990-01-01",
    "aliases.dateOfBirth,1990-01-02",
    "sentenceStartDate,2024-01-01",
    "releaseDate,2024-01-02",
    "confirmedReleaseDate,2024-01-03",
    "licenceExpiryDate,2024-01-05",
    "homeDetentionCurfewEligibilityDate,2024-01-06",
    "homeDetentionCurfewActualDate,2024-01-07",
    "homeDetentionCurfewEndDate,2024-01-08",
    "topupSupervisionStartDate,2024-01-09",
    "topupSupervisionExpiryDate,2024-01-10",
    "nonDtoReleaseDate,2024-01-11",
    "receptionDate,2024-01-12",
    "paroleEligibilityDate,2024-01-13",
    "automaticReleaseDate,2024-01-14",
    "postRecallReleaseDate,2024-01-15",
    "conditionalReleaseDate,2024-01-16",
    "actualParoleDate,2024-01-17",
    "tariffDate,2024-01-18",
    "releaseOnTemporaryLicenceDate,2024-01-19",
    "dischargeDate,2024-01-20",
    "currentIncentive.nextReviewDate,2024-01-21",
    "addresses.startDate,2024-01-22",
  )
  fun `date fields`(field: String, value: String) {
    val request = RequestDsl {
      query {
        dateMatcher(field EQ value)
      }
    }

    webTestClient.attributeSearch(request).expectSingleResult(field, value)
  }

  @ParameterizedTest
  @CsvSource(
    "currentIncentive.dateTime,2023-12-01T00:00:00,2022-11-10T15:47:24",
  )
  fun `date time fields`(field: String, lessThanTime: LocalDateTime, expectedTime: String) {
    val request = RequestDsl {
      query {
        dateTimeMatcher(field LT lessThanTime)
      }
    }

    webTestClient.attributeSearch(request).expectSingleResult(field, expectedTime)
  }

  private fun WebTestClient.attributeSearch(request: AttributeSearchRequest) =
    post()
      .uri {
        it.path("/attribute-search")
          .queryParam("page", 0)
          .queryParam("size", 1)
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk

  private fun WebTestClient.ResponseSpec.expectSingleResult(field: String, value: Any) {
    val attribute =
      field.takeIf { it.contains(".") }
        ?.takeIf { !it.contains("currentIncentive") }
        ?.replace(".", "[0].")
        ?: field
    expectBody()
      .jsonPath("$.content[0].$attribute").isEqualTo(value)
  }
}
