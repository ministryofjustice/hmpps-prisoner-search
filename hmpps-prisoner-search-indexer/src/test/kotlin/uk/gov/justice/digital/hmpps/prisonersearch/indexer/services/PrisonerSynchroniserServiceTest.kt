@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.Agency
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.Alert
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.AlertCodeSummary
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.ComplexityOfNeed
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDocumentSummary
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.ConvictedStatusEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class PrisonerSynchroniserServiceTest {
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val alertsService = mock<AlertsService>()
  private val complexityOfNeedService = mock<ComplexityOfNeedService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val prisonerDifferenceService = mock<PrisonerDifferenceService>()
  private val prisonerMovementsEventService = mock<PrisonerMovementsEventService>()
  private val alertsUpdatedEventService = mock<AlertsUpdatedEventService>()
  private val convictedStatusEventService = mock<ConvictedStatusEventService>()
  private val prisonRegisterService = mock<PrisonRegisterService>()
  private val domainEventEmitter = mock<HmppsDomainEventEmitter>()

  private val service = PrisonerSynchroniserService(
    prisonerRepository,
    telemetryClient,
    restrictedPatientService,
    incentivesService,
    alertsService,
    complexityOfNeedService,
    prisonRegisterService,
    prisonerDifferenceService,
    prisonerMovementsEventService,
    alertsUpdatedEventService,
    convictedStatusEventService,
    domainEventEmitter,
  )

  @Nested
  inner class ReindexUpdate {
    private val booking = OffenderBookingBuilder().anOffenderBooking()
    private val prisonerNumber = booking.offenderNo
    val existingPrisoner = Prisoner().apply { bookingId = booking.bookingId.toString() }
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(
        prisonerNumber,
        existingPrisoner,
        sequenceNumber = 0,
        primaryTerm = 0,
      )

    @Test
    fun `will update prisoner to index`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      service.reindexUpdate(booking, "event")

      verify(prisonerRepository, times(1)).updatePrisoner(
        eq(booking.offenderNo),
        isA(),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create prisoner if not present`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(null)
      service.reindexUpdate(booking, "event")

      verify(prisonerRepository, times(1)).createPrisoner(isA())
      verify(prisonerMovementsEventService).generateAnyEvents(isNull(), any(), eq(booking))
      verify(convictedStatusEventService).generateAnyEvents(isNull(), any())
    }

    @Test
    fun `will generate domain events`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(prisonerRepository.updatePrisoner(any(), any(), isA())).thenReturn(true)
      service.reindexUpdate(booking, "event")

      verify(prisonerMovementsEventService).generateAnyEvents(eq(existingPrisoner), any(), eq(booking))
      verify(convictedStatusEventService).generateAnyEvents(eq(existingPrisoner), any())
    }

    @Test
    fun `will not save prisoner if no changes`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(prisonerRepository.updatePrisoner(any(), any(), isA())).thenReturn(false)
      service.reindexUpdate(booking, "event")

      verify(prisonerRepository).updatePrisoner(any(), any(), eq(prisonerDocumentSummary))
      verify(prisonerDifferenceService, never()).getDifferencesByCategory<Prisoner>(any(), any())
      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "12345"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will update domain data if nomis booking id has changed`() {
      val changedBookingId = 112233L
      val newIncentive =
        uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel("NEW", "Desc", LocalDateTime.now(), null)
      val prisonerDocumentSummaryAfterUpdate = PrisonerDocumentSummary(
        prisonerNumber,
        Prisoner().apply { bookingId = changedBookingId.toString() },
        sequenceNumber = 0,
        primaryTerm = 0,
      )

      whenever(prisonerRepository.getSummary(any()))
        .thenReturn(prisonerDocumentSummary, prisonerDocumentSummaryAfterUpdate)
      whenever(
        prisonerRepository.updatePrisoner(
          eq(prisonerNumber),
          any(),
          eq(prisonerDocumentSummary),
        ),
      )
        .thenReturn(true)
      whenever(incentivesService.getCurrentIncentive(changedBookingId)).thenReturn(newIncentive)

      service.reindexUpdate(booking.copy(bookingId = changedBookingId), "event")

      verify(prisonerRepository, times(1)).updateIncentive(
        eq(prisonerNumber),
        eq(newIncentive.toCurrentIncentive()),
        eq(prisonerDocumentSummaryAfterUpdate),
      )
    }

    @Test
    fun `will generate domain events if nomis booking id has changed and a domain update fails`() {
      val changedBookingId = 112233L
      val prisonerDocumentSummaryAfterUpdate = PrisonerDocumentSummary(
        prisonerNumber,
        Prisoner().apply { bookingId = changedBookingId.toString() },
        sequenceNumber = 0,
        primaryTerm = 0,
      )
      whenever(prisonerRepository.getSummary(any())).thenReturn(
        prisonerDocumentSummary,
        prisonerDocumentSummaryAfterUpdate,
      )
      whenever(prisonerRepository.updatePrisoner(eq(prisonerNumber), any(), eq(prisonerDocumentSummary)))
        .thenReturn(true)
      whenever(
        prisonerRepository.updateRestrictedPatient(
          eq(prisonerNumber),
          eq(false),
          isNull(),
          isNull(),
          isNull(),
          isNull(),
          isNull(),
          isNull(),
          eq(prisonerDocumentSummaryAfterUpdate),
        ),
      ).thenThrow(RuntimeException("test"))

      val changedBooking = booking.copy(bookingId = changedBookingId)

      assertThrows<RuntimeException> { service.reindexUpdate(changedBooking, "event") }

      verify(prisonerMovementsEventService).generateAnyEvents(
        eq(existingPrisoner),
        any(),
        eq(changedBooking),
      )
      verify(convictedStatusEventService).generateAnyEvents(
        eq(existingPrisoner),
        any(),
      )
    }
  }

  @Nested
  inner class ReindexIncentive {
    private val prisonerNumber = "A1234AA"
    private val bookingId = 2L
    private val oldIncentive = CurrentIncentive(IncentiveLevel("OLD", "Desc"), LocalDateTime.now(), null)
    private val newIncentive =
      uk.gov.justice.digital.hmpps.prisonersearch.common.dps.IncentiveLevel("NEW", "Desc", LocalDateTime.now(), null)
    val prisoner = Prisoner().apply {
      bookingId = this@ReindexIncentive.bookingId.toString()
      currentIncentive = oldIncentive
    }
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(prisonerNumber, prisoner, sequenceNumber = 0, primaryTerm = 0)

    @BeforeEach
    fun setup() {
      whenever(prisonerRepository.copyPrisoner(any())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `will save incentive to current index`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      service.reindexIncentive(prisonerNumber, "event")

      verify(prisonerRepository).updateIncentive(
        eq(prisonerNumber),
        isA(),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create telemetry`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexIncentive(prisonerNumber, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.INCENTIVE_UPDATED.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "2"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will generate domain events`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq(prisonerNumber), any(), any())).thenReturn(true)
      service.reindexIncentive(prisonerNumber, "event")

      verify(prisonerDifferenceService).generateDiffEvent(eq(prisoner), eq(prisonerNumber), eq(prisoner))
    }

    @Test
    fun `will not save prisoner if no changes`() {
      whenever(prisonerRepository.getSummary(eq(prisonerNumber))).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq(prisonerNumber), any(), any())).thenReturn(false)
      service.reindexIncentive(prisonerNumber, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.INCENTIVE_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "2"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(null)

      service.reindexIncentive(prisonerNumber, "event")

      verifyNoInteractions(incentivesService)
      verify(prisonerRepository, never()).updateIncentive(any(), any(), any())
    }

    @Test
    fun `Updates ok when no incentive data`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(null)

      service.reindexIncentive(prisonerNumber, "event")

      verify(prisonerRepository).updateIncentive(eq("A1234AA"), isNull(), eq(prisonerDocumentSummary))
    }

    @Test
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexIncentive(prisonerNumber, "event")

      verify(prisonerDifferenceService).generateDiffEvent(
        eq(prisonerDocumentSummary.prisoner),
        eq(prisonerDocumentSummary.prisonerNumber!!),
        eq(prisonerDocumentSummary.prisoner!!),
      )
      verifyNoMoreInteractions(prisonerDifferenceService)
    }
  }

  @Nested
  inner class ReindexRestrictedPatient {
    private val prisonerNumber = "A1234AA"
    val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking(offenderNo = prisonerNumber).copy(
      assignedLivingUnit = AssignedLivingUnit(
        agencyId = "OUT",
        locationId = 1,
        description = "Outside",
        agencyName = "Outside Prison",
      ),
    )
    private val bookingId = outsidePrisoner.bookingId
    private val newRestrictedPatient = RestrictedPatient(
      "SWI",
      Agency("HOS2", null, null, "HOSP", true),
      LocalDate.parse("2024-10-25"),
      null,
    )
    val prisoner = Prisoner().apply {
      bookingId = this@ReindexRestrictedPatient.bookingId.toString()
      supportingPrisonId = "MDI"
    }
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(prisonerNumber, prisoner, sequenceNumber = 0, primaryTerm = 0)

    @BeforeEach
    fun setup() {
      whenever(prisonerRepository.copyPrisoner(any())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `will call restricted patients and save RP to current index if prisoner is outside`() {
      whenever(prisonerRepository.getSummary(prisonerNumber)).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)

      whenever(
        prisonerRepository.updateRestrictedPatient(
          eq(prisonerNumber),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
        ),
      ).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verify(restrictedPatientService).getRestrictedPatient(prisonerNumber)
      verify(prisonerRepository).updateRestrictedPatient(
        eq(prisonerNumber),
        eq(true),
        isA(),
        isA(),
        isNull(),
        isA(),
        isNull(),
        any(),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will generate domain events`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)
      whenever(
        prisonerRepository.updateRestrictedPatient(
          eq(prisonerNumber),
          any(),
          isA(),
          isA(),
          isNull(),
          isA(),
          isNull(),
          any(),
          any(),
        ),
      ).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verify(prisonerDifferenceService).generateDiffEvent(eq(prisoner), eq(prisonerNumber), eq(prisoner))
    }

    @Test
    fun `will create telemetry`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)
      whenever(
        prisonerRepository.updateRestrictedPatient(
          eq(prisonerNumber),
          any(),
          isA(),
          isA(),
          isNull(),
          isA(),
          isNull(),
          any(),
          any(),
        ),
      ).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.RESTRICTED_PATIENT_UPDATED.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", prisonerNumber),
            entry("bookingId", bookingId.toString()),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will not save prisoner if no changes`() {
      whenever(prisonerRepository.getSummary(eq(prisonerNumber))).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)
      whenever(
        prisonerRepository.updateRestrictedPatient(
          eq("A1234AA"),
          any(),
          isA(),
          isA(),
          isA(),
          isA(),
          isA(),
          any(),
          any(),
        ),
      ).thenReturn(false)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.RESTRICTED_PATIENT_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", prisonerNumber),
            entry("bookingId", bookingId.toString()),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(null)

      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verifyNoInteractions(incentivesService)
      verify(prisonerRepository, never()).updateIncentive(any(), any(), any())
    }

    @Test
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, "event")

      verifyNoInteractions(prisonerDifferenceService)
    }

    @Test
    fun `will not call restricted patients if prisoner in a prison`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking(offenderNo = prisonerNumber).copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.reindexRestrictedPatient(prisonerNumber, prisonBooking, "event")

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking(offenderNo = prisonerNumber).copy(
        assignedLivingUnit = null,
      )

      service.reindexRestrictedPatient(noLivingUnitBooking.offenderNo, noLivingUnitBooking, "event")

      verifyNoInteractions(restrictedPatientService)
    }
  }

  @Nested
  inner class ReindexAlerts {
    private val prisonerNumber = "A1234AA"
    private val bookingId = 2L
    private val oldAlerts = listOf(
      PrisonerAlert(
        alertType = "TYPE",
        alertCode = "OLD",
        active = true,
        expired = false,
      ),
    )
    private val newApiAlerts =
      listOf(
        Alert(
          alertUuid = UUID.fromString("00001111-2222-3333-4444-000000000001"),
          prisonNumber = "A1234AA",
          alertCode = AlertCodeSummary(
            alertTypeCode = "TYPE",
            alertTypeDescription = "Alert type description",
            code = "NEW",
            description = "Alert code description",
          ),
          description = "Alert description",
          authorisedBy = "A. Nurse, An Agency",
          activeFrom = LocalDate.parse("2021-09-27"),
          activeTo = LocalDate.parse("2022-07-15"),
          isActive = true,
          createdAt = LocalDateTime.parse("2021-09-27T14:19:25"),
          createdBy = "USER1234",
          createdByDisplayName = "Firstname Lastname",
          lastModifiedAt = LocalDateTime.parse("2022-07-15T15:24:56"),
          lastModifiedBy = "USER1234",
          lastModifiedByDisplayName = "Firstname Lastname",
          activeToLastSetAt = LocalDateTime.parse("2022-07-15T15:24:56"),
          activeToLastSetBy = "USER123",
          activeToLastSetByDisplayName = "Firstname Lastname",
        ),
      )
    val prisoner = Prisoner().apply {
      bookingId = this@ReindexAlerts.bookingId.toString()
      alerts = oldAlerts
    }
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(prisonerNumber, prisoner, sequenceNumber = 0, primaryTerm = 0)

    @BeforeEach
    fun setup() {
      whenever(prisonerRepository.copyPrisoner(any())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `will save alert to index`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(alertsService.getActiveAlertsForPrisoner(prisonerNumber)).thenReturn(newApiAlerts)
      service.reindexAlerts(prisonerNumber, "event")

      verify(prisonerRepository).updateAlerts(
        eq(prisonerNumber),
        eq(
          newApiAlerts.map {
            PrisonerAlert(
              alertType = "TYPE",
              alertCode = "NEW",
              active = true,
              expired = true,
            )
          },
        ),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create telemetry`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(alertsService.getActiveAlertsForPrisoner(prisonerNumber)).thenReturn(newApiAlerts)
      whenever(prisonerRepository.updateAlerts(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexAlerts(prisonerNumber, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.ALERTS_UPDATED.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "2"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will generate domain events`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(alertsService.getActiveAlertsForPrisoner(prisonerNumber)).thenReturn(newApiAlerts)
      whenever(prisonerRepository.updateAlerts(eq(prisonerNumber), any(), any())).thenReturn(true)
      service.reindexAlerts(prisonerNumber, "event")

      verify(prisonerDifferenceService).generateAlertDiffEvent(any(), eq(prisonerNumber), any())
      verify(alertsUpdatedEventService).generateAnyEvents(any(), any(), eq(prisoner))
    }

    @Test
    fun `will not save alerts if no changes`() {
      whenever(prisonerRepository.getSummary(eq(prisonerNumber))).thenReturn(prisonerDocumentSummary)
      whenever(alertsService.getActiveAlertsForPrisoner(prisonerNumber)).thenReturn(newApiAlerts)
      whenever(prisonerRepository.updateAlerts(eq(prisonerNumber), any(), any())).thenReturn(false)
      service.reindexAlerts(prisonerNumber, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.ALERTS_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "2"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(null)

      service.reindexAlerts(prisonerNumber, "event")

      verifyNoInteractions(alertsService)
      verify(prisonerRepository, never()).updateAlerts(any(), any(), any())
    }

    @Test
    fun `Updates ok when no alerts data`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(prisonerDocumentSummary)
      whenever(alertsService.getActiveAlertsForPrisoner(prisonerNumber)).thenReturn(null)

      service.reindexAlerts(prisonerNumber, "event")

      verify(prisonerRepository).updateAlerts(eq("A1234AA"), isNull(), eq(prisonerDocumentSummary))
    }
  }

  @Nested
  inner class ReindexComplexityOfNeed {
    private val femaleBooking = OffenderBookingBuilder().anOffenderBooking().copy(agencyId = "BZI")
    private val maleBooking = OffenderBookingBuilder().anOffenderBooking()

    private val prisonerNumber = femaleBooking.offenderNo
    private val newComplexityOfNeed = ComplexityOfNeed(prisonerNumber, "medium", true)
    val prisoner = Prisoner().apply {
      bookingId = bookingId.toString()
      complexityOfNeedLevel = "old-value"
    }
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(prisonerNumber, prisoner, sequenceNumber = 0, primaryTerm = 0)

    @BeforeEach
    fun setup() {
      whenever(prisonerRepository.getSummary(eq(prisonerNumber))).thenReturn(prisonerDocumentSummary)

      whenever(prisonRegisterService.getAllPrisons()).thenReturn(
        listOf(
          PrisonDto(prisonId = "BZI", active = true, male = false, female = true),
          PrisonDto(prisonId = "MDI", active = true, male = true, female = false),
        ),
      )
    }

    @Test
    fun `will save ComplexityOfNeed level to current index`() {
      whenever(complexityOfNeedService.getComplexityOfNeedForPrisoner(prisonerNumber)).thenReturn(newComplexityOfNeed)
      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verify(prisonerRepository).updateComplexityOfNeed(
        eq(prisonerNumber),
        eq(newComplexityOfNeed.level),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create telemetry`() {
      whenever(complexityOfNeedService.getComplexityOfNeedForPrisoner(prisonerNumber)).thenReturn(newComplexityOfNeed)
      whenever(prisonerRepository.updateComplexityOfNeed(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.COMPLEXITY_OF_NEED_UPDATED.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "not set"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will not save prisoner if no changes`() {
      whenever(complexityOfNeedService.getComplexityOfNeedForPrisoner(prisonerNumber)).thenReturn(newComplexityOfNeed)
      whenever(prisonerRepository.updateComplexityOfNeed(eq(prisonerNumber), any(), any())).thenReturn(false)
      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.COMPLEXITY_OF_NEED_OPENSEARCH_NO_CHANGE.name),
        check {
          assertThat(it).containsOnly(
            entry("prisonerNumber", "A1234AA"),
            entry("bookingId", "not set"),
            entry("event", "event"),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will do nothing if prisoner not found`() {
      whenever(prisonerRepository.getSummary(any())).thenReturn(null)

      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verify(prisonerRepository, never()).updateComplexityOfNeed(any(), any(), any())
    }

    @Test
    fun `Updates ok when no data`() {
      whenever(complexityOfNeedService.getComplexityOfNeedForPrisoner(prisonerNumber)).thenReturn(null)

      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verify(prisonerRepository).updateComplexityOfNeed(eq("A1234AA"), isNull(), eq(prisonerDocumentSummary))
    }

    @Test
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(complexityOfNeedService.getComplexityOfNeedForPrisoner(prisonerNumber)).thenReturn(newComplexityOfNeed)
      whenever(prisonerRepository.updateComplexityOfNeed(eq("A1234AA"), any(), any())).thenReturn(true)
      service.reindexComplexityOfNeedWithGet(femaleBooking, "event")

      verifyNoInteractions(prisonerDifferenceService)
    }

    @Test
    fun `will not call complexity of need for male prison`() {
      service.reindexComplexityOfNeedWithGet(maleBooking, "event")

      verifyNoInteractions(complexityOfNeedService)
    }

    @Test
    fun `will not call complexity of need for prisoner who is OUT`() {
      service.reindexComplexityOfNeedWithGet(femaleBooking.copy(agencyId = "OUT"), "event")

      verifyNoInteractions(complexityOfNeedService)
    }
  }

  @Nested
  inner class Index {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    fun `will save prisoner to current index`() {
      service.index(booking)

      verify(prisonerRepository).save(isA())
    }

    @Test
    fun `will not send telemetry event for insert`() {
      service.index(booking)

      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `will not call restricted patients if prisoner in a prison`() {
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.index(prisonBooking)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.index(noLivingUnitBooking)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will call restricted patients if prisoner is outside`() {
      val outsideBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )

      service.index(outsideBooking)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `will throw exception if restricted patients service call fails`() {
      val outsideBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index(outsideBooking) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }

    @Test
    fun `will not call incentives if no booking id`() {
      val noBookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.index(noBookingIdBooking)

      verifyNoInteractions(incentivesService)
    }

    @Test
    fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.index(bookingIdBooking)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    fun `will throw exception if incentives service call fails`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index(booking) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }
  }

  @Nested
  inner class CompareAndMaybeIndex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @BeforeEach
    fun setUp() {
      whenever(prisonerRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `will do nothing if no differences found`() {
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(false)
      whenever(prisonerRepository.get(any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, Result.success(null), Result.success(null), Result.success(null), Result.success(null))

      verify(prisonerRepository, never()).save(any())
      verify(prisonerDifferenceService).hasChanged(any(), any())
      verifyNoMoreInteractions(prisonerDifferenceService)
    }

    @Test
    fun `will report differences if found`() {
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      whenever(prisonerRepository.get(any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, Result.success(null), Result.success(null), Result.success(null), Result.success(null))

      verify(prisonerDifferenceService).reportDiffTelemetry(any(), any())
      verify(prisonerRepository).save(any())
    }

    @Test
    fun `will generate events if differences found`() {
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      val existingPrisoner = Prisoner()
      whenever(prisonerRepository.get(any())).thenReturn(existingPrisoner)

      service.compareAndMaybeIndex(booking, Result.success(null), Result.success(null), Result.success(null), Result.success(null))

      verify(prisonerMovementsEventService).generateAnyEvents(eq(existingPrisoner), any(), eq(booking))
      verify(alertsUpdatedEventService).generateAnyEvents(eq(existingPrisoner), any())
      verify(convictedStatusEventService).generateAnyEvents(eq(existingPrisoner), any())
    }
  }

  @Nested
  inner class Refresh {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    fun `will get incentive level if booking present`() {
      service.refresh(booking)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    fun `will not get incentive if there is no booking`() {
      service.refresh(OffenderBookingBuilder().anOffenderBooking(null))

      verifyNoInteractions(incentivesService)
    }

    @Test
    fun `will not call restricted patients if prisoner in a prison`() {
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.refresh(prisonBooking)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.refresh(noLivingUnitBooking)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will call restricted patients if prisoner is outside`() {
      val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )

      service.refresh(outsidePrisoner)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `will call complexity of need for female prison`() {
      val femalePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(agencyId = "BZI")
      whenever(prisonRegisterService.getAllPrisons()).thenReturn(
        listOf(
          PrisonDto(prisonId = "BZI", active = true, male = false, female = true),
          PrisonDto(prisonId = "MDI", active = true, male = true, female = false),
        ),
      )

      service.refresh(femalePrisoner)

      verify(complexityOfNeedService).getComplexityOfNeedForPrisoner("A1234AA")
    }
  }

  @Nested
  inner class Delete {
    @Test
    fun `will raise a PRISONER_REMOVED telemetry event`() {
      whenever(prisonerRepository.delete("ABC123D")).thenReturn(true)

      service.delete("ABC123D")

      verify(telemetryClient).trackEvent(
        TelemetryEvents.PRISONER_REMOVED.name,
        mapOf("prisonerNumber" to "ABC123D"),
        null,
      )
    }

    @Test
    fun `will raise a PRISONER_OPENSEARCH_NO_CHANGE telemetry event`() {
      whenever(prisonerRepository.delete("NEVER-EXISTED")).thenReturn(false)

      service.delete("NEVER-EXISTED")

      verify(telemetryClient).trackEvent(
        TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE.name,
        mapOf("prisonerNumber" to "NEVER-EXISTED"),
        null,
      )
    }

    @Test
    fun `will call prisoner repository to delete the prisoner`() {
      service.delete("ABC123D")

      verify(prisonerRepository).delete("ABC123D")
    }
  }
}
