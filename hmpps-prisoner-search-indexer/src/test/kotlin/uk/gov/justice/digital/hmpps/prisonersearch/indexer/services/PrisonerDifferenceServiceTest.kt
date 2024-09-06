package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlias
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_DATABASE_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerHashRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.LocalDate
import java.time.LocalDateTime

class PrisonerDifferenceServiceTest {

  private val telemetryClient = mock<TelemetryClient>()
  private val domainEventsEmitter = mock<HmppsDomainEventEmitter>()
  private val diffProperties = mock<DiffProperties>()
  private val prisonerHashRepository = mock<PrisonerHashRepository>()
  private val objectMapper = mock<ObjectMapper>()
  private val prisonerMovementsEventService = mock<PrisonerMovementsEventService>()
  private val alertsUpdatedEventService = mock<AlertsUpdatedEventService>()
  private val prisonerDifferencesRepository = mock<PrisonerDifferencesRepository>()

  private val prisonerDifferenceService = PrisonerDifferenceService(
    telemetryClient,
    domainEventsEmitter,
    diffProperties,
    prisonerHashRepository,
    objectMapper,
    prisonerMovementsEventService,
    alertsUpdatedEventService,
    prisonerDifferencesRepository,
  )

  @Nested
  inner class HandleDifferences {
    @BeforeEach
    fun setUp() {
      whenever(diffProperties.events).thenReturn(true)
    }

    @Test
    fun `should send event if prisoner hash has changed`() {
      whenever(
        prisonerHashRepository.upsertIfChanged(
          anyString(),
          anyString(),
          any(),
        ),
      ).thenReturn(1)
      whenever(objectMapper.writeValueAsString(any()))
        .thenReturn("hash1")
        .thenReturn("hash2")
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      prisonerDifferenceService.handleDifferences(prisoner1, someOffenderBooking(), prisoner2, "eventType")

      verify(prisonerHashRepository).upsertIfChanged(eq("someOffenderNo"), anyString(), any())
      verify(domainEventsEmitter).emitPrisonerDifferenceEvent(eq("someOffenderNo"), anyMap())
    }

    @Test
    fun `should raise no-change telemetry if there are no changes using database`() {
      whenever(
        prisonerHashRepository.upsertIfChanged(anyString(), anyString(), any()),
      ).thenReturn(0)
      whenever(objectMapper.writeValueAsString(any())).thenReturn("a_string").thenReturn("b_string")
      val prisoner = Prisoner().apply { pncNumber = "somePnc1" }

      prisonerDifferenceService.handleDifferences(prisoner, someOffenderBooking(), prisoner, "eventType")

      verify(telemetryClient).trackEvent(
        eq(PRISONER_DATABASE_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "someOffenderNo"),
            entry("bookingId", "not set"),
            entry("event", "eventType"),
          )
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class GetDiff {
    @Test
    fun `should find no differences`() {
      val prisoner1 = Prisoner().apply { pncNumber = "any" }
      val prisoner2 = Prisoner().apply { pncNumber = "any" }

      assertThat(prisoner1.diff(prisoner2)).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", "somePnc1", "somePnc2"))
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro1"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
      }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("croNumber", "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report differences of different property types`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        firstName = "firstName1"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        firstName = "firstName2"
      }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", "somePnc1", "somePnc2"),
          Tuple("firstName", "firstName1", "firstName2"),
        )
    }

    @Test
    fun `should report boolean difference`() {
      val prisoner1 = Prisoner().apply { youthOffender = true }
      val prisoner2 = Prisoner().apply { youthOffender = false }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("youthOffender", true, false))
    }

    @Test
    fun `should handle null difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc" }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("pncNumber", null, "somePnc"))
    }

    @Test
    fun `should ignore null equality`() {
      val prisoner1 = Prisoner().apply { pncNumber = null }
      val prisoner2 = Prisoner().apply { pncNumber = null }

      assertThat(prisoner1.diff(prisoner2).diffs).isEmpty()
    }

    @Test
    fun `should handle list difference`() {
      val prisoner1 = Prisoner().apply { aliases = listOf() }
      val prisoner2 = Prisoner().apply {
        aliases = listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now()))
      }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(
          Tuple(
            "aliases",
            listOf<PrisonerAlias>(),
            listOf(alias(firstName = "aliasFirstName", lastName = "aliasLastName", dateOfBirth = LocalDate.now())),
          ),
        )
    }

    private fun alias(firstName: String, lastName: String, dateOfBirth: LocalDate) =
      PrisonerAlias(
        title = null,
        firstName = firstName,
        middleNames = null,
        lastName = lastName,
        dateOfBirth = dateOfBirth,
        gender = null,
        ethnicity = null,
        raceCode = null,
      )

    @Test
    fun `should handle LocalDate difference`() {
      val prisoner1 = Prisoner().apply { sentenceStartDate = LocalDate.of(2022, 9, 12) }
      val prisoner2 = Prisoner().apply { sentenceStartDate = LocalDate.of(2021, 8, 11) }

      assertThat(prisoner1.diff(prisoner2).diffs)
        .extracting("fieldName", "left", "right")
        .containsExactly(Tuple("sentenceStartDate", LocalDate.of(2022, 9, 12), LocalDate.of(2021, 8, 11)))
    }
  }

  @Nested
  inner class Groupings {
    @Test
    fun `groups properties by property type`() {
      assertThat(prisonerDifferenceService.propertiesByDiffCategory[DiffCategory.IDENTIFIERS]).contains(
        "pncNumber",
        "croNumber",
      )
      assertThat(prisonerDifferenceService.propertiesByDiffCategory[DiffCategory.PERSONAL_DETAILS]).contains("firstName")
    }

    @Test
    fun `maps property types by property`() {
      assertThat(prisonerDifferenceService.diffCategoriesByProperty["pncNumber"]).isEqualTo(DiffCategory.IDENTIFIERS)
      assertThat(prisonerDifferenceService.diffCategoriesByProperty["croNumber"]).isEqualTo(DiffCategory.IDENTIFIERS)
      assertThat(prisonerDifferenceService.diffCategoriesByProperty["firstName"]).isEqualTo(DiffCategory.PERSONAL_DETAILS)
    }
  }

  @Nested
  inner class GetDifferencesByCategory {
    @Test
    fun `should report zero differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc"
        croNumber = "someCro"
        firstName = "someName"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc"
        croNumber = "someCro"
        firstName = "someName"
      }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType).isEmpty()
    }

    @Test
    fun `should report single difference`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro"
        firstName = "someName"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro"
        firstName = "someName"
      }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
        )
    }

    @Test
    fun `should allow null differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc"
        croNumber = null
        firstName = "someName"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = null
        croNumber = "someCro"
        firstName = "someName"
      }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc", null),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, null, "someCro"),
        )
    }

    @Test
    fun `should report multiple differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro1"
        firstName = "someName"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someName"
      }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactly(DiffCategory.IDENTIFIERS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, "someCro1", "someCro2"),
        )
    }

    @Test
    fun `should report multiple differences of multiple property types`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro1"
        firstName = "someName1"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someName2"
      }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffCategory.IDENTIFIERS, DiffCategory.PERSONAL_DETAILS)
      val identifierDiffs = diffsByType[DiffCategory.IDENTIFIERS]
      val personalDetailDiffs = diffsByType[DiffCategory.PERSONAL_DETAILS]

      assertThat(identifierDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("pncNumber", DiffCategory.IDENTIFIERS, "somePnc1", "somePnc2"),
          Tuple("croNumber", DiffCategory.IDENTIFIERS, "someCro1", "someCro2"),
        )
      assertThat(personalDetailDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          Tuple("firstName", DiffCategory.PERSONAL_DETAILS, "someName1", "someName2"),
        )
    }

    @Test
    fun `should report alerts differences`() {
      val prisoner1 = Prisoner().apply { alerts = listOf(alert()) }
      val prisoner2 = Prisoner().apply { alerts = listOf(alert(active = false)) }

      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffCategory.ALERTS)
      val alertsDiffs = diffsByType[DiffCategory.ALERTS]

      assertThat(alertsDiffs)
        .extracting("property", "categoryChanged", "oldValue", "newValue")
        .containsExactlyInAnyOrder(
          (Tuple("alerts", DiffCategory.ALERTS, listOf(alert()), listOf(alert(active = false)))),
        )
    }

    @Test
    fun `should report incentive differences`() {
      val prisoner1 = Prisoner().apply {
        currentIncentive = CurrentIncentive(
          level = IncentiveLevel(code = "STD", description = "Standard"),
          dateTime = LocalDateTime.of(2020, 1, 1, 0, 0),
          nextReviewDate = LocalDate.of(2020, 10, 1),
        )
      }
      val prisoner2 = Prisoner().apply {
        currentIncentive = CurrentIncentive(
          level = IncentiveLevel(code = "ENH", description = "Enhanced"),
          dateTime = LocalDateTime.of(2020, 1, 1, 0, 0),
          nextReviewDate = LocalDate.of(2020, 10, 1),
        )
      }
      val diffsByType = prisonerDifferenceService.getDifferencesByCategory(prisoner1, prisoner2)

      assertThat(diffsByType.keys).containsExactlyInAnyOrder(DiffCategory.INCENTIVE_LEVEL)
    }

    private fun alert(
      alertType: String = "SOME_TYPE",
      alertCode: String = "SOME_CODE",
      active: Boolean = true,
      expired: Boolean = false,
    ) =
      PrisonerAlert(alertType, alertCode, active, expired)
  }

  @Nested
  inner class GenerateDifferencesTelemetry {
    val booking = someOffenderBooking()

    @Test
    fun `should report identifiers`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")

      verify(telemetryClient).trackEvent(
        eq("PRISONER_UPDATED"),
        check<Map<String, String>> {
          assertThat(it["prisonerNumber"]).isEqualTo("someOffenderNo")
          assertThat(it["bookingId"]).isEqualTo("not set")
          assertThat(it["event"]).isEqualTo("eventType")
        },
        isNull(),
      )
    }

    @Test
    fun `should report a single difference`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")

      verify(telemetryClient).trackEvent(
        eq("PRISONER_UPDATED"),
        check<Map<String, String>> {
          assertThat(it["categoriesChanged"]).isEqualTo("[IDENTIFIERS]")
        },
        isNull(),
      )
    }

    @Test
    fun `should report null differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc"
        croNumber = null
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = null
        croNumber = "someCro"
      }

      prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")

      verify(telemetryClient).trackEvent(
        eq("PRISONER_UPDATED"),
        check<Map<String, String>> {
          assertThat(it["categoriesChanged"]).isEqualTo("[IDENTIFIERS]")
        },
        isNull(),
      )
    }

    @Test
    fun `should report multiple differences of multiple types`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro1"
        firstName = "someFirstName1"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someFirstName2"
      }

      prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")

      verify(telemetryClient)
        .trackEvent(
          eq("PRISONER_UPDATED"),
          check<Map<String, String>> {
            assertThat(it["categoriesChanged"]).isEqualTo("[IDENTIFIERS, PERSONAL_DETAILS]")
          },
          isNull(),
        )
    }

    @Test
    fun `should send telemetry created event`() {
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someFirstName2"
      }

      prisonerDifferenceService.generateDiffTelemetry(null, booking.offenderNo, 12345, prisoner2, "eventType")

      verify(telemetryClient).trackEvent(
        eq("PRISONER_CREATED"),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "someOffenderNo"),
            entry("bookingId", "12345"),
            entry("event", "eventType"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should raise no-change found telemetry if there are no changes`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc1" }

      prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")

      verify(telemetryClient).trackEvent(
        eq("PRISONER_UPDATED_NO_DIFFERENCES"),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "someOffenderNo"),
            entry("bookingId", "not set"),
            entry("event", "eventType"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `should swallow exceptions when raising telemetry`() {
      whenever(telemetryClient.trackEvent(anyString(), anyMap(), isNull())).thenThrow(RuntimeException::class.java)

      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      assertDoesNotThrow {
        prisonerDifferenceService.generateDiffTelemetry(prisoner1, booking.offenderNo, booking.bookingId, prisoner2, "eventType")
      }
    }
  }

  @Nested
  inner class ReportDiffTelemetry {
    @Test
    fun `should report a single difference`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        prisonerNumber = "A1234ZZ"
      }
      val prisoner2 = Prisoner().apply {
        prisonerNumber = "A1234ZZ"
        pncNumber = "somePnc2"
      }

      prisonerDifferenceService.reportDiffTelemetry(prisoner1, prisoner2)

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.DIFFERENCE_REPORTED.name),
        check<Map<String, String>> {
          assertThat(it["prisonerNumber"]).isEqualTo("A1234ZZ")
          assertThat(it["categoriesChanged"]).isEqualTo("[IDENTIFIERS]")
        },
        isNull(),
      )

      verify(prisonerDifferencesRepository).save(
        check {
          assertThat(it.differences).isEqualTo(
            "[[pncNumber: somePnc1, somePnc2]]",
          )
        },
      )
    }

    @Test
    fun `should report null differences`() {
      val prisoner1 = Prisoner().apply {
        prisonerNumber = "A1234BC"
        pncNumber = "somePnc"
        croNumber = null
      }
      val prisoner2 = Prisoner().apply {
        prisonerNumber = "A1234BC"
        pncNumber = null
        croNumber = "someCro"
      }

      prisonerDifferenceService.reportDiffTelemetry(prisoner1, prisoner2)

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.DIFFERENCE_REPORTED.name),
        check<Map<String, String>> {
          assertThat(it["categoriesChanged"]).isEqualTo("[IDENTIFIERS]")
        },
        isNull(),
      )

      verify(prisonerDifferencesRepository).save(
        check {
          assertThat(it.differences).isEqualTo(
            "[[croNumber: null, someCro], [pncNumber: somePnc, null]]",
          )
        },
      )
    }

    @Test
    fun `should report multiple differences of multiple types`() {
      val prisoner1 = Prisoner().apply {
        prisonerNumber = "A1234BC"
        pncNumber = "somePnc1"
        alerts = listOf(
          PrisonerAlert(alertType = "X", alertCode = "X1", expired = false, active = true),
        )
        currentIncentive = CurrentIncentive(
          level = IncentiveLevel("STANDARD", "Standard"),
          dateTime = LocalDateTime.parse("2021-05-09T10:00:00"),
        )
        shoeSize = 10
      }
      val prisoner2 = Prisoner().apply {
        prisonerNumber = "A1234BC"
        pncNumber = "somePnc2"
        alerts = listOf(
          PrisonerAlert(alertType = "Y", alertCode = "X1", expired = false, active = true),
        )
        firstName = "someFirstName2"
        sentenceStartDate = LocalDate.parse("2023-05-09")
        shoeSize = 11
      }

      prisonerDifferenceService.reportDiffTelemetry(prisoner1, prisoner2)

      verify(telemetryClient)
        .trackEvent(
          eq(TelemetryEvents.DIFFERENCE_REPORTED.name),
          check<Map<String, String>> {
            assertThat(it["categoriesChanged"]).isEqualTo(
              "[ALERTS, IDENTIFIERS, INCENTIVE_LEVEL, PERSONAL_DETAILS, PHYSICAL_DETAILS, SENTENCE]",
            )
          },
          isNull(),
        )

      verify(prisonerDifferencesRepository).save(
        check {
          assertThat(it.differences).isEqualTo(
            "[[alerts: [PrisonerAlert(alertType=X, alertCode=X1, active=true, expired=false)]," +
              " [PrisonerAlert(alertType=Y, alertCode=X1, active=true, expired=false)]]," +
              " [currentIncentive: CurrentIncentive(level=IncentiveLevel(code=STANDARD, description=Standard), dateTime=2021-05-09T10:00, nextReviewDate=null), null]," +
              " [firstName: null, someFirstName2], [pncNumber: somePnc1, somePnc2]," +
              " [sentenceStartDate: null, 2023-05-09]," +
              " [shoeSize: 10, 11]]",
          )
        },
      )
    }

    @Test
    fun `should raise no telemetry if there are no changes`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc1" }

      prisonerDifferenceService.reportDiffTelemetry(prisoner1, prisoner2)

      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `prisoner does not exist in index`() {
      val prisoner2 = Prisoner().apply { prisonerNumber = "B1234YY" }

      prisonerDifferenceService.reportDiffTelemetry(null, prisoner2)

      verify(telemetryClient)
        .trackEvent(
          eq(TelemetryEvents.DIFFERENCE_MISSING.name),
          check<Map<String, String>> {
            assertThat(it["prisonerNumber"]).isEqualTo("B1234YY")
          },
          isNull(),
        )
    }
  }

  @Nested
  inner class GenerateDifferencesEvent {
    val booking = someOffenderBooking()

    @BeforeEach
    fun setUp() {
      whenever(diffProperties.events).thenReturn(true)
    }

    @Test
    fun `should report identifiers`() {
      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      prisonerDifferenceService.generateDiffEvent(prisoner1, booking.offenderNo, prisoner2)

      verify(domainEventsEmitter).emitPrisonerDifferenceEvent(
        eq("someOffenderNo"),
        check {
          assertThat(it.keys).containsExactly(DiffCategory.IDENTIFIERS)
        },
      )
    }

    @Test
    fun `should report null differences`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc"
        croNumber = null
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = null
        croNumber = "someCro"
      }

      prisonerDifferenceService.generateDiffEvent(prisoner1, booking.offenderNo, prisoner2)

      verify(domainEventsEmitter).emitPrisonerDifferenceEvent(
        eq("someOffenderNo"),
        check {
          assertThat(it.keys).containsExactly(DiffCategory.IDENTIFIERS)
        },
      )
    }

    @Test
    fun `should report multiple types`() {
      val prisoner1 = Prisoner().apply {
        pncNumber = "somePnc1"
        croNumber = "someCro1"
        firstName = "someFirstName1"
      }
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someFirstName2"
      }

      prisonerDifferenceService.generateDiffEvent(prisoner1, booking.offenderNo, prisoner2)

      verify(domainEventsEmitter).emitPrisonerDifferenceEvent(
        eq("someOffenderNo"),
        check {
          assertThat(it.keys).containsExactlyInAnyOrder(DiffCategory.IDENTIFIERS, DiffCategory.PERSONAL_DETAILS)
        },
      )
    }

    @Test
    fun `should send created event`() {
      val prisoner2 = Prisoner().apply {
        pncNumber = "somePnc2"
        croNumber = "someCro2"
        firstName = "someFirstName2"
      }

      prisonerDifferenceService.generateDiffEvent(null, booking.offenderNo, prisoner2)

      verify(domainEventsEmitter).emitPrisonerCreatedEvent("someOffenderNo")
    }

    @Test
    fun `should not create events if feature switch is off`() {
      whenever(diffProperties.events).thenReturn(false)

      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      prisonerDifferenceService.generateDiffEvent(prisoner1, booking.offenderNo, prisoner2)

      verify(domainEventsEmitter, never()).emitPrisonerDifferenceEvent(anyString(), any())
    }

    @Test
    fun `should NOT swallow exceptions when sending domain events`() {
      whenever(
        domainEventsEmitter.emitPrisonerDifferenceEvent(
          anyString(),
          any(),
        ),
      ).thenThrow(RuntimeException::class.java)

      val prisoner1 = Prisoner().apply { pncNumber = "somePnc1" }
      val prisoner2 = Prisoner().apply { pncNumber = "somePnc2" }

      assertThatThrownBy {
        prisonerDifferenceService.generateDiffEvent(prisoner1, booking.offenderNo, prisoner2)
      }.isInstanceOf(RuntimeException::class.java)
    }
  }

  private fun someOffenderBooking() =
    OffenderBooking(
      offenderId = 1L,
      offenderNo = "someOffenderNo",
      firstName = "someFirstName",
      lastName = "someLastName",
      dateOfBirth = LocalDate.now(),
    )
}
