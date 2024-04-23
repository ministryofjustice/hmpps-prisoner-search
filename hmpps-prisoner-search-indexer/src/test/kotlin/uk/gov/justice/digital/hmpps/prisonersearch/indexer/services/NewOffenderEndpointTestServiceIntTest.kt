package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.AliasBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PhysicalCharacteristicBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.PrisonerBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.wiremock.PrisonApiExtension.Companion.prisonApi

class NewOffenderEndpointTestServiceIntTest : IntegrationTestBase() {

  @Nested
  inner class BuildIndex {
    @Test
    fun `should not publish diff telemetry`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", firstName = "LUCAS")
      prisonApi.stubOffenders(prisonerBuilder)
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder)
      buildAndSwitchIndex(GREEN, 1)

      verify(telemetryClient, never()).trackEvent(
        eq("OffenderBookingDifference"),
        check {
          assertThat(it).containsEntry("offenderNo", "A1234BC")
          assertThat(it).containsEntry("diff", "firstName")
        },
        isNull(),
      )
    }

    @Test
    fun `should publish diff telemetry for a difference on the new endpoint`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", firstName = "LUCAS")
      prisonApi.stubOffenders(prisonerBuilder)
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder.copy(firstName = "MICK"))
      buildAndSwitchIndex(GREEN, 1)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "firstName")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `should publish diff telemetry for a difference to a list`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", aliases = listOf(AliasBuilder(gender = "Male")))
      prisonApi.stubOffenders(prisonerBuilder)
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder.copy(aliases = listOf(AliasBuilder(gender = "Female"))))
      buildAndSwitchIndex(GREEN, 1)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "aliases")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `should publish diff telemetry for a difference to a nested object`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", physicalCharacteristics = PhysicalCharacteristicBuilder(hairColour = "Brown"))
      prisonApi.stubOffenders(prisonerBuilder)
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder.copy(physicalCharacteristics = PhysicalCharacteristicBuilder(hairColour = "Blonde")))
      buildAndSwitchIndex(GREEN, 1)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "physicalCharacteristics")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `should publish diff telemetry for prisoner not found on new endpoint`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", firstName = "LUCAS")
      prisonApi.stubOffenders(prisonerBuilder)
      prisonApi.stubGetOffenderNewEndpointNotFound("A1234BC")
      buildAndSwitchIndex(GREEN, 1)

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "new_booking_null")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class IndexPrisoner {
    @Test
    fun `should publish diff telemetry`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", firstName = "LUCAS")
      prisonApi.stubOffenders(prisonerBuilder)
      buildAndSwitchIndex(GREEN, 1)

      prisonApi.stubOffenders(prisonerBuilder.copy(firstName = "MIKE"))
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder.copy(firstName = "MICK"))
      webTestClient.put()
        .uri("/maintain-index/index-prisoner/A1234BC")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isOk

      verify(maintainIndexService).indexPrisoner("A1234BC")
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "firstName")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class RefreshIndex {
    @Test
    fun `should publish diff telemetry`() {
      val prisonerBuilder = PrisonerBuilder("A1234BC", firstName = "LUCAS")
      prisonApi.stubOffenders(prisonerBuilder)
      buildAndSwitchIndex(GREEN, 1)

      prisonApi.stubOffenders(prisonerBuilder.copy(firstName = "MIKE"))
      prisonApi.stubGetOffenderNewEndpoint(prisonerBuilder.copy(firstName = "MICK"))
      webTestClient.put()
        .uri("/refresh-index")
        .accept(MediaType.APPLICATION_JSON)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_INDEX")))
        .exchange()
        .expectStatus().isAccepted

      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("OffenderBookingDifference"),
          check {
            assertThat(it).containsEntry("offenderNo", "A1234BC")
            assertThat(it).containsEntry("diff", "firstName")
          },
          isNull(),
        )
      }
    }
  }
}
