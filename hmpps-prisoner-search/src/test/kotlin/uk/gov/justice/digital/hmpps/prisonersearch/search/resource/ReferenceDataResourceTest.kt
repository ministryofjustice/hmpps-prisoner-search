@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.reset
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.BodyPartBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.IncentiveLevelBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalMarkBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.ProfileInformationBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.build
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.category
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.csra
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.ethnicity
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.facialHair
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.gender
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.hairColour
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.imprisonmentStatusDescription
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.inOutStatus
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.incentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.leftEyeColour
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.legalStatus
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.maritalStatus
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.marksBodyPart
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.nationality
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.religion
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.rightEyeColour
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.scarsBodyPart
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.shapeOfFace
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.status
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.tattoosBodyPart
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute.youthOffender
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataResponse
import java.util.stream.Stream

class ReferenceDataResourceTest : AbstractSearchIntegrationTest() {
  override fun loadPrisonerData() {
    val prisonerData = listOf(
      PrisonerBuilder(
        prisonerNumber = "G7089EZ",
        agencyId = "LEI",
        cellLocation = "B-C1-010",
        gender = "Male",
        aliases = listOf(AliasBuilder(gender = "Not Known / Not Recorded")),
        physicalCharacteristics = PhysicalCharacteristicBuilder(
          hairColour = "Red",
          rightEyeColour = "Green",
          leftEyeColour = "Hazel",
          facialHair = "Clean Shaven",
          shapeOfFace = "Round",
          build = "Proportional",
          shoeSize = 4,
        ),
        physicalMarks = PhysicalMarkBuilder(
          tattoo = listOf(BodyPartBuilder("Ankle", "rose"), BodyPartBuilder("Knee")),
          scar = listOf(BodyPartBuilder("Finger"), BodyPartBuilder("Foot")),
          mark = listOf(BodyPartBuilder("Lip", "too much")),
        ),
        profileInformation = ProfileInformationBuilder(
          religion = "Jedi Knight",
          nationality = "British",
          youthOffender = true,
          maritalStatus = "Single-not married/in civil partnership",
        ),
        category = "C",
        csra = "High",
        currentIncentive = IncentiveLevelBuilder(levelCode = "ENH", levelDescription = "Enhanced"),
      ),
      PrisonerBuilder(
        prisonerNumber = "G7090AC",
        agencyId = "AGI",
        cellLocation = "H-1-004",
        gender = "Female",
        ethnicity = "White: Any other background",
        physicalCharacteristics = PhysicalCharacteristicBuilder(
          hairColour = "Balding",
          rightEyeColour = "Clouded",
          leftEyeColour = "Brown",
          facialHair = "Goatee Beard",
          shapeOfFace = "Bullet",
          build = "Obese",
          shoeSize = 6,
        ),
        physicalMarks = PhysicalMarkBuilder(
          tattoo = listOf(BodyPartBuilder("Finger", "rose"), BodyPartBuilder("Foot")),
          scar = listOf(BodyPartBuilder("Toe", "nasty looking scar"), BodyPartBuilder("Knee")),
          mark = listOf(BodyPartBuilder("Torso", "birthmark on chest")),
        ),
        profileInformation = ProfileInformationBuilder(
          religion = "Agnostic",
          nationality = "Irish",
          maritalStatus = "Married",
        ),
        category = "Q",
        csra = "Low",
        currentIncentive = IncentiveLevelBuilder(levelCode = "BAS", levelDescription = "Basic"),
      ),
      PrisonerBuilder(
        prisonerNumber = "G7090AD",
        agencyId = "AGI",
        cellLocation = "H-1-004",
        gender = "Not Known / Not Recorded",
        physicalCharacteristics = PhysicalCharacteristicBuilder(
          hairColour = "Red",
          rightEyeColour = "Green",
          leftEyeColour = "Hazel",
          facialHair = "Clean Shaven",
          shapeOfFace = "Round",
          build = "Proportional",
          shoeSize = 9,
        ),
        physicalMarks = PhysicalMarkBuilder(
          tattoo = listOf(BodyPartBuilder("Ankle", "rose"), BodyPartBuilder("Knee")),
          scar = listOf(BodyPartBuilder("Finger"), BodyPartBuilder("Foot")),
          mark = listOf(BodyPartBuilder("Lip", "too much")),
        ),
      ),
      PrisonerBuilder(
        prisonerNumber = "G7090BA",
        agencyId = "LEI",
        cellLocation = "B-C1-010",
        gender = "Male",
        ethnicity = "Prefer not to say",
        aliases = listOf(AliasBuilder(ethnicity = "White: Any other background")),
        physicalCharacteristics = PhysicalCharacteristicBuilder(
          hairColour = "Mouse",
          rightEyeColour = "Missing",
          leftEyeColour = "Missing",
          facialHair = "Not Asked",
          shapeOfFace = "Oval",
          build = "Muscular",
          shoeSize = 13,
        ),
        physicalMarks = PhysicalMarkBuilder(
          tattoo = listOf(BodyPartBuilder("Ankle", "dragon"), BodyPartBuilder("Knee")),
          scar = listOf(BodyPartBuilder("Finger"), BodyPartBuilder("Foot")),
          mark = listOf(BodyPartBuilder("Lip", "too much")),
        ),
      ),
      PrisonerBuilder(
        prisonerNumber = "G7090BC",
        agencyId = "AGI",
        cellLocation = "H-1-004",
        gender = "Female",
        ethnicity = "Prefer not to say",
        physicalCharacteristics = PhysicalCharacteristicBuilder(
          hairColour = "Red",
          rightEyeColour = "Green",
          leftEyeColour = "Hazel",
          facialHair = "Clean Shaven",
          shapeOfFace = "Round",
          build = "Proportional",
          shoeSize = 1,
        ),
        physicalMarks = PhysicalMarkBuilder(
          tattoo = listOf(BodyPartBuilder("Knee", "dragon"), BodyPartBuilder("Knee")),
          scar = listOf(BodyPartBuilder("Finger"), BodyPartBuilder("Foot")),
          mark = listOf(BodyPartBuilder("Lip", "too much")),
        ),
      ),
    )
    loadPrisonersFromBuilders(prisonerData)
  }

  @Test
  fun `access forbidden when no authority`() {
    webTestClient.get().uri("/reference-data")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.get().uri("/reference-data/gender")
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when reference data attribute not found`() {
    webTestClient.get().uri("/reference-data/gobbledygook")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.get().uri("/reference-data/gender")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.get().uri("/reference-data/gender")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  private fun attributeData(): Stream<Arguments> =
    Stream.of(
      arguments(build, listOf("Muscular", "Obese", "Proportional"), null),
      arguments(category, listOf("C", "Q"), null),
      arguments(csra, listOf("High", "Low"), null),
      arguments(ethnicity, listOf("Prefer not to say", "White: Any other background"), null),
      arguments(facialHair, listOf("Clean Shaven", "Goatee Beard", "Not Asked"), null),
      arguments(gender, listOf("Female", "Male", "Not Known / Not Recorded"), null),
      arguments(hairColour, listOf("Balding", "Mouse", "Red"), null),
      arguments(imprisonmentStatusDescription, listOf("Life imprisonment"), null),
      arguments(incentiveLevel, listOf("Basic", "Enhanced"), null),
      arguments(inOutStatus, listOf("IN"), listOf("Inside")),
      arguments(leftEyeColour, listOf("Brown", "Hazel", "Missing"), null),
      arguments(legalStatus, listOf("REMAND"), listOf("Remand")),
      arguments(marksBodyPart, listOf("Lip", "Torso"), null),
      arguments(maritalStatus, listOf("Married", "Single-not married/in civil partnership"), null),
      arguments(nationality, listOf("British", "Irish"), null),
      arguments(religion, listOf("Agnostic", "Jedi Knight"), null),
      arguments(rightEyeColour, listOf("Clouded", "Green", "Missing"), null),
      arguments(scarsBodyPart, listOf("Finger", "Foot", "Knee", "Toe"), null),
      arguments(shapeOfFace, listOf("Bullet", "Oval", "Round"), null),
      arguments(status, listOf("ACTIVE IN"), listOf("Active Inside")),
      arguments(tattoosBodyPart, listOf("Ankle", "Finger", "Foot", "Knee"), null),
      arguments(youthOffender, listOf("false", "true"), listOf("No", "Yes")),
    )

  @ParameterizedTest
  @MethodSource("attributeData")
  fun `find by attribute`(
    attribute: ReferenceDataAttribute,
    expectedValues: List<String>,
    expectedLabels: List<String>?,
  ): Unit = referenceDataRequest(
    attribute = attribute.name,
    expectedValues = expectedValues,
    expectedLabels = expectedLabels ?: expectedValues,
  )

  @Test
  fun `ensure all attributes are tested`() {
    assertThat(ReferenceDataAttribute.entries.map { it.name }).containsExactlyInAnyOrderElementsOf(
      attributeData().toList().map { it.get()[0].toString() },
    )
  }

  @Test
  fun `test caching of attribute values`() {
    cacheManager.getCache("referenceData")?.clear()

    // pre-check to ensure that forcing elastic error causes search to fail
    forceElasticError()
    webTestClient.get().uri("/reference-data/${leftEyeColour.name}")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().is5xxServerError

    // so that we can check caching then works
    reset(elasticsearchClient)
    referenceDataRequest(attribute = leftEyeColour.name, expectedValues = listOf("Brown", "Hazel", "Missing"))

    // and no exception is thrown
    forceElasticError()
    referenceDataRequest(attribute = leftEyeColour.name, expectedValues = listOf("Brown", "Hazel", "Missing"))
  }

  private fun referenceDataRequest(
    attribute: String,
    expectedValues: List<String> = emptyList(),
    expectedLabels: List<String> = expectedValues,
  ) {
    val response = webTestClient.get().uri("/reference-data/$attribute")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(ReferenceDataResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.data).extracting("value").containsExactlyElementsOf(expectedValues)
    assertThat(response.data).extracting("label").containsExactlyElementsOf(expectedLabels)
    assertThat(response.data).size().isEqualTo(expectedValues.size)
  }
}
