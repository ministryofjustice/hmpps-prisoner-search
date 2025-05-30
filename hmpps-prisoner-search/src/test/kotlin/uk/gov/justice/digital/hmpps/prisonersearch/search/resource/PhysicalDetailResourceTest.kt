@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.search.resource

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.search.AbstractSearchIntegrationTest
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.BodyPartBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PhysicalMarkBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.search.model.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.BodyPart
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PaginationRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto.PhysicalDetailRequest

class PhysicalDetailResourceTest : AbstractSearchIntegrationTest() {
  override fun loadPrisonerData() {
    val prisonerData = listOf(
      // height / weight test data
      PrisonerBuilder(
        prisonerNumber = "H1090AA",
        agencyId = "MDI",
        cellLocation = "H-1-004",
        heightCentimetres = 202,
        weightKilograms = 100,
      ),
      PrisonerBuilder(
        prisonerNumber = "H7089EY",
        agencyId = "MDI",
        cellLocation = "A-1-001",
        heightCentimetres = 165,
        weightKilograms = 57,
      ),
      PrisonerBuilder(
        prisonerNumber = "H7089EZ",
        agencyId = "LEI",
        cellLocation = "B-C1-010",
        heightCentimetres = 188,
        weightKilograms = 99,
      ),
      PrisonerBuilder(
        prisonerNumber = "H7090BA",
        agencyId = "LEI",
        cellLocation = "B-C1-010",
        heightCentimetres = 200,
        weightKilograms = 99,
      ),
      PrisonerBuilder(
        prisonerNumber = "H7090BB",
        agencyId = "MDI",
        cellLocation = "A-1-003",
        heightCentimetres = 200,
        weightKilograms = 80,
      ),
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
          mark = listOf(BodyPartBuilder("Torso", "birthmark on chest")),
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
    webTestClient.post().uri("/physical-detail")
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access forbidden when no role`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("LEI", "MDI")),
      )
      .headers(setAuthorisation())
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `bad request when no filtering prison IDs provided`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = emptyList()))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `bad request when multiple prisons and cell location prefix supplied`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI", "LEI"), cellLocationPrefix = "ABC-1"),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `can perform a detail search for ROLE_GLOBAL_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `can perform a detail search for ROLE_PRISONER_SEARCH role`() {
    webTestClient.post().uri("/physical-detail")
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI")))
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `will page the results - first page limited to size`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      minHeight = 100,
      prisonIds = listOf("MDI", "LEI"),
      pagination = PaginationRequest(0, 2),
    ),
    expectedPrisoners = listOf("H1090AA", "H7089EY"),
  )

  @Test
  fun `will page the results - second page shows remaining prisoners`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      minHeight = 100,
      prisonIds = listOf("MDI", "LEI"),
      pagination = PaginationRequest(1, 2),
    ),
    expectedPrisoners = listOf("H7089EZ", "H7090BA"),
  )

  @Test
  fun `find by cell location with prison prefix`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "MDI-A"),
    expectedPrisoners = listOf("H7089EY", "H7090BB"),
  )

  @Test
  fun `find by cell location without prison prefix`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "A"),
    expectedPrisoners = listOf("H7089EY", "H7090BB"),
  )

  @Test
  fun `bad request when invalid response fields requested`() {
    webTestClient.post()
      .uri {
        it.path("/physical-detail")
          .queryParam("responseFields", listOf("prisonerNumber", "doesNotExist"))
          .build()
      }
      .bodyValue(
        PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "ABC-1"),
      )
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isBadRequest
      .expectBody().jsonPath("userMessage").value<String> {
        assertThat(it).contains("Invalid response fields requested: [doesNotExist]")
      }
  }

  @Test
  fun `returns requested response fields only`() {
    webTestClient.post()
      .uri {
        it.path("/physical-detail")
          .queryParam("responseFields", listOf("prisonerNumber", "lastName"))
          .build()
      }
      .bodyValue(PhysicalDetailRequest(minHeight = 100, prisonIds = listOf("MDI"), cellLocationPrefix = "MDI-A"))
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("content.length()").isEqualTo(2)
      .jsonPath("content[0].prisonerNumber").isEqualTo("H7089EY")
      .jsonPath("content[0].lastName").isEqualTo("MORALES")
      .jsonPath("content[0].firstName").doesNotExist()
      .jsonPath("content[1].prisonerNumber").isEqualTo("H7090BB")
      .jsonPath("content[1].lastName").isEqualTo("MORALES")
      .jsonPath("content[1].firstName").doesNotExist()
  }

  @Nested
  inner class `height and weight tests` {
    @Test
    fun `bad request when min height less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minHeight = -100, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when max height less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(maxHeight = -100, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when heights inverted`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minHeight = 100, maxHeight = 50, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when weights inverted`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minWeight = 100, maxWeight = 50, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when min weight less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minWeight = -100, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when max weight less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(maxWeight = -200, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `find by minimum height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 170, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `find by maximum height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7089EY", "H7090BB"),
    )

    @Test
    fun `find by exact height`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 200, maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7090BB"),
    )

    @Test
    fun `find by height range`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minHeight = 100, maxHeight = 200, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H7089EY", "H7090BB"),
    )

    @Test
    fun `find by minimum weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 70, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `find by maximum weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(maxWeight = 100, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7089EY", "H7090BB"),
    )

    @Test
    fun `find by exact weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 100, maxWeight = 100, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA"),
    )

    @Test
    fun `find by weight range`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    )

    @Test
    fun `height and weight are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf("H1090AA", "H7090BB"),
    ) {
      assertThat(it).extracting("heightCentimetres").containsExactly(202, 200)
      assertThat(it).extracting("weightKilograms").containsExactly(100, 80)
    }

    @Test
    fun `lenient find by height and weight`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minWeight = 80, maxWeight = 150, maxHeight = 200, lenient = true, prisonIds = listOf("MDI")),
      expectedPrisoners = listOf(
        // matches on height and weight so appears first
        "H7090BB",
        // matches only on weight
        "H1090AA",
        // matches only on weight
        "H7089EY",
      ),
    )
  }

  @Nested
  inner class `gender and ethnicity tests` {
    @Test
    fun `find by gender`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Female", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AC", "G7090BC"),
    )

    @Test
    fun `find by gender ignores female when searching for male`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Male", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf(),
    )

    @Test
    fun `find by gender includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(gender = "Not Known / Not Recorded", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("G7090AD", "G7089EZ"),
    )

    @Test
    fun `find by ethnicity`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(ethnicity = "Prefer not to say", prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("G7090BA", "G7090BC"),
    )

    @Test
    fun `find by ethnicity includes aliases`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    )

    @Test
    fun `gender and ethnicity are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("gender").containsExactly("Female", "Male")
      assertThat(it).extracting("ethnicity").containsExactly("White: Any other background", "Prefer not to say")
    }

    @Test
    fun `no lenient search returns no matches for gender and ethnicity`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        gender = "Not Known / Not Recorded",
        prisonIds = listOf("AGI", "LEI"),
        lenient = false,
      ),
      expectedPrisoners = listOf(),
    )

    @Test
    fun `lenient search returns partial matches for gender and ethnicity`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White",
        gender = "Not Known / Not Recorded",
        prisonIds = listOf("AGI", "LEI"),
        lenient = true,
      ),
      expectedPrisoners = listOf(
        // gender not known
        "G7090AD",
        // ethnicity white: any other
        "G7090AC",
        // alias gender not known
        "G7089EZ",
        // alias ethnicity white: any other
        "G7090BA",
      ),
    )
  }

  @Nested
  inner class `physical characteristics tests` {
    @Test
    fun `find by hair colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(hairColour = "Red", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by right eye colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(rightEyeColour = "Green", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by left eye colour`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(leftEyeColour = "Hazel", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by facial hair`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(facialHair = "Clean Shaven", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by shape of face`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(shapeOfFace = "Round", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `find by build`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(build = "Proportional", prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AD", "G7090BC"),
    )

    @Test
    fun `bad request when shoe size inverted`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minShoeSize = 100, maxShoeSize = 50, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when min shoe size less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(minShoeSize = -100, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when max shoe size less than 0`() {
      webTestClient.post().uri("/physical-detail")
        .bodyValue(
          PhysicalDetailRequest(maxShoeSize = -200, prisonIds = listOf("MDI")),
        )
        .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `find by minimum shoe size`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minShoeSize = 6, prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AC", "G7090AD"),
    )

    @Test
    fun `find by maximum shoe size`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(maxShoeSize = 7, prisonIds = listOf("AGI")),
      expectedPrisoners = listOf("G7090AC", "G7090BC"),
    )

    @Test
    fun `find by exact shoe size`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minShoeSize = 13, maxShoeSize = 13, prisonIds = listOf("LEI")),
      expectedPrisoners = listOf("G7090BA"),
    )

    @Test
    fun `find by shoe size range`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(minShoeSize = 5, maxShoeSize = 9, prisonIds = listOf("AGI", "LEI")),
      expectedPrisoners = listOf("G7090AC", "G7090AD"),
    )

    @Test
    fun `physical characteristics are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("hairColour").containsExactly("Balding", "Mouse")
      assertThat(it).extracting("rightEyeColour").containsExactly("Clouded", "Missing")
      assertThat(it).extracting("leftEyeColour").containsExactly("Brown", "Missing")
      assertThat(it).extracting("facialHair").containsExactly("Goatee Beard", "Not Asked")
      assertThat(it).extracting("shapeOfFace").containsExactly("Bullet", "Oval")
      assertThat(it).extracting("build").containsExactly("Obese", "Muscular")
      assertThat(it).extracting("shoeSize").containsExactly(6, 13)
    }
  }

  @Nested
  inner class `physical marks tests` {
    @Test
    fun `searching by tattoos with a body part`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        tattoos = listOf(BodyPart("Ankle")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7089EZ", "G7090AD", "G7090BA"),
    )

    @Test
    fun `searching by tattoos with a body part and a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        tattoos = listOf(BodyPart(bodyPart = "Ankle", comment = "rose")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7089EZ", "G7090AD"),
    )

    // TODO I think this is wrong - neither prisoner has a single tattoo with both Knee and rose
    @Test
    fun `searching by tattoos with a body part and a comment on different tattoos`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        tattoos = listOf(BodyPart(bodyPart = "Knee", comment = "rose")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7089EZ", "G7090AD"),
    )

    @Test
    fun `searching by tattoos with a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        tattoos = listOf(BodyPart(comment = "rose")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7089EZ", "G7090AC", "G7090AD"),
    )

    @Test
    fun `tattoos are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("tattoos").containsExactly(
        listOf(mapOf("bodyPart" to "Finger", "comment" to "rose"), mapOf("bodyPart" to "Foot")),
        listOf(mapOf("bodyPart" to "Ankle", "comment" to "dragon"), mapOf("bodyPart" to "Knee")),
      )
    }

    @Test
    fun `searching by scars with a body part`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        scars = listOf(BodyPart("Finger")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7089EZ", "G7090AD", "G7090BA", "G7090BC"),
    )

    @Test
    fun `searching by scars with a body part and a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        scars = listOf(BodyPart(bodyPart = "Ankle", comment = "nasty")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC"),
    )

    @Test
    fun `searching by scars with a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        scars = listOf(BodyPart(comment = "nasty")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC"),
    )

    @Test
    fun `scars are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("scars").containsExactly(
        listOf(mapOf("bodyPart" to "Ankle", "comment" to "nasty looking scar"), mapOf("bodyPart" to "Knee")),
        listOf(mapOf("bodyPart" to "Finger"), mapOf("bodyPart" to "Foot")),
      )
    }

    @Test
    fun `searching by marks with a body part`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        marks = listOf(BodyPart("Torso")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC"),
    )

    @Test
    fun `searching by marks with a body part and a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        marks = listOf(BodyPart(bodyPart = "Torso", comment = "birthmark")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC"),
    )

    @Test
    fun `searching by marks with a comment`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        marks = listOf(BodyPart(comment = "birthmark")),
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC"),
    )

    @Test
    fun `marks are returned in search results`(): Unit = physicalDetailSearch(
      detailRequest = PhysicalDetailRequest(
        ethnicity = "White: Any other background",
        prisonIds = listOf("AGI", "LEI"),
      ),
      expectedPrisoners = listOf("G7090AC", "G7090BA"),
    ) {
      assertThat(it).extracting("marks").containsExactly(
        listOf(mapOf("bodyPart" to "Torso", "comment" to "birthmark on chest")),
        listOf(mapOf("bodyPart" to "Lip", "comment" to "too much")),
      )
    }
  }

  @Test
  fun `lenient search doesn't have any mandatory fields`(): Unit = physicalDetailSearch(
    detailRequest = PhysicalDetailRequest(
      prisonIds = listOf("AGI", "LEI", "MDI"),
      lenient = true,
      ethnicity = "february",
      gender = "february",
      minHeight = 10,
      maxHeight = 200,
      minWeight = 10,
      maxWeight = 210,
      minShoeSize = 1,
      maxShoeSize = 2,
      hairColour = "february",
      rightEyeColour = "february",
      leftEyeColour = "february",
      facialHair = "february",
      shapeOfFace = "february",
      build = "february",
      tattoos = listOf(BodyPart(bodyPart = "february")),
      scars = listOf(BodyPart(bodyPart = "february")),
      marks = listOf(BodyPart(bodyPart = "february")),
    ),
    expectedPrisoners = listOf("H7089EY", "H7089EZ", "H7090BA", "H7090BB", "H1090AA", "G7090BC", "G7089EZ", "G7090AC", "G7090AD", "G7090BA"),
  )

  private fun physicalDetailSearch(
    detailRequest: PhysicalDetailRequest,
    expectedPrisoners: List<String> = emptyList(),
    extraContentAssertions: (param: MutableList<out Any?>?) -> Unit = {},
  ) {
    val response = webTestClient.post().uri("/physical-detail")
      .bodyValue(detailRequest)
      .headers(setAuthorisation(roles = listOf("ROLE_GLOBAL_SEARCH")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody(RestResponsePage::class.java)
      .returnResult().responseBody

    assertThat(response.content).extracting("prisonerNumber").containsExactlyElementsOf(expectedPrisoners)
    assertThat(response.content).size().isEqualTo(expectedPrisoners.size)
    assertThat(response.numberOfElements).isEqualTo(expectedPrisoners.size)
    extraContentAssertions(response.content)
  }
}
