package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.restrictedpatients.model.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.RestrictedPatientsApiExtension.Companion.restrictedPatientsApi
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(RestrictedPatientService::class, WebClientConfiguration::class)
internal class RestrictedPatientsServiceTest {
  @Autowired
  private lateinit var restrictedPatientService: RestrictedPatientService

  // mappings defined in src/test/resources/restricted-patients/mappings directory

  @Test
  internal fun `will supply authentication token`() {
    restrictedPatientService.getRestrictedPatient("123456L")

    restrictedPatientsApi.verify(
      getRequestedFor(urlEqualTo("/restricted-patient/prison-number/123456L"))
        .withHeader("Authorization", equalTo("Bearer ABCDE")),

    )
  }

  @Test
  internal fun `will return restricted patient`() {
    val restrictedPatient = restrictedPatientService.getRestrictedPatient("A123ZZZ")!!
    restrictedPatientsApi.verifyGetRestrictedPatientRequest("A123ZZZ")

    assertThat(restrictedPatient.dischargeTime).isEqualTo(LocalDateTime.parse("2020-10-10T21:00:00"))
    assertThat(restrictedPatient.commentText).isEqualTo("Prisoner was released on bail")
    assertThat(restrictedPatient.hospitalLocation).isEqualTo(
      Agency("HAZLWD", "Hazelwood House", "Hazelwood House", "HSHOSP", true),
    )
    assertThat(restrictedPatient.supportingPrison?.agencyId).isEqualTo("MDI")
  }

  @Test
  internal fun `will return null if restricted patient not found`() {
    // default in mock server mappings is to return not found

    val restrictedPatient = restrictedPatientService.getRestrictedPatient("A1234BC")
    assertThat(restrictedPatient).isNull()
  }
}
