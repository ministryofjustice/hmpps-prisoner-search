@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchDataIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.BodyPartBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalMarkBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.ProfileInformationBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataResponse
import java.util.stream.Stream

class ReferenceDataResourceTest : AbstractSearchDataIntegrationTest() {

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
          other = listOf(BodyPartBuilder("Head", "left ear missing")),
          mark = listOf(BodyPartBuilder("Lip", "too much")),
        ),
        profileInformation = ProfileInformationBuilder(
          religion = "Jedi Knight",
          nationality = "British",
        ),
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
          scar = listOf(BodyPartBuilder("Ankle", "nasty looking scar"), BodyPartBuilder("Knee")),
          other = listOf(BodyPartBuilder("Nose", "bent to the right")),
          mark = listOf(BodyPartBuilder("Torso", "birthmark on chest")),
        ),
        profileInformation = ProfileInformationBuilder(
          religion = "Agnostic",
          nationality = "Irish",
        ),
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
          other = listOf(BodyPartBuilder("Head", "left ear missing")),
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
          other = listOf(BodyPartBuilder("Head", "left ear missing")),
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
          other = listOf(BodyPartBuilder("Head", "left ear missing")),
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
      arguments("build", listOf("Proportional", "Muscular", "Obese")),
      arguments("ethnicity", listOf("White: Any other background", "Prefer not to say")),
      arguments("facialHair", listOf("Clean Shaven", "Goatee Beard", "Not Asked")),
      arguments("gender", listOf("Female", "Male", "Not Known / Not Recorded")),
      arguments("hairColour", listOf("Red", "Balding", "Mouse")),
      arguments("imprisonmentStatusDescription", listOf("Life imprisonment")),
      arguments("inOutStatus", listOf("IN")),
      arguments("leftEyeColour", listOf("Hazel", "Brown", "Missing")),
      arguments("legalStatus", listOf("REMAND")),
      arguments("nationality", listOf("British", "Irish")),
      arguments("religion", listOf("Agnostic", "Jedi Knight")),
      arguments("rightEyeColour", listOf("Green", "Clouded", "Missing")),
      arguments("shapeOfFace", listOf("Round", "Bullet", "Oval")),
      arguments("status", listOf("ACTIVE IN")),
      arguments("youthOffender", listOf("false")),
    )

  @ParameterizedTest
  @MethodSource("attributeData")
  fun `find by attribute`(attribute: String, expectedResults: List<String>): Unit = referenceDataRequest(
    attribute = attribute,
    expectedResults = expectedResults,
  )

  private fun referenceDataRequest(
    attribute: String,
    expectedResults: List<String> = emptyList(),
  ) {
    val response = webTestClient.get().uri("/reference-data/$attribute")
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(ReferenceDataResponse::class.java)
      .returnResult().responseBody!!

    assertThat(response.data).extracting("key").containsExactlyInAnyOrderElementsOf(expectedResults)
    assertThat(response.data).size().isEqualTo(expectedResults.size)
  }
}
