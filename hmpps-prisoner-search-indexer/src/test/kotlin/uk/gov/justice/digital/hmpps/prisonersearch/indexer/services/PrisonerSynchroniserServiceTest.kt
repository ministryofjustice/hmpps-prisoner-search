@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
import uk.gov.justice.digital.hmpps.prisonersearch.common.dps.RestrictedPatient
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IncentiveLevel
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.RED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.toCurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.nomis.AssignedLivingUnit
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_OPENSEARCH_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.model.OffenderBookingBuilder
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel.GREEN_BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDocumentSummary
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.Result

private val LABEL = GREEN_BLUE

internal class PrisonerSynchroniserServiceTest {
  private val incentivesService = mock<IncentivesService>()
  private val restrictedPatientService = mock<RestrictedPatientService>()
  private val prisonerRepository = mock<PrisonerRepository>()
  private val telemetryClient = mock<TelemetryClient>()
  private val prisonerDifferenceService = mock<PrisonerDifferenceService>()
  private val prisonerMovementsEventService = mock<PrisonerMovementsEventService>()
  private val alertsUpdatedEventService = mock<AlertsUpdatedEventService>()

  private val service = PrisonerSynchroniserService(
    prisonerRepository,
    telemetryClient,
    restrictedPatientService,
    incentivesService,
    prisonerDifferenceService,
    prisonerMovementsEventService,
    alertsUpdatedEventService,
  )

  @Nested
  inner class Reindex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    fun `will save prisoner to repository`() {
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerRepository, times(1)).save(isA(), isA())
    }

    @Test
    fun `will save prisoner to current index`() {
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerRepository).save(isA(), check { assertThat(it).isEqualTo(GREEN) })
    }

    @Test
    fun `will not save prisoner if no changes`() {
      val existingPrisoner = Prisoner()
      whenever(prisonerRepository.get(any(), any())).thenReturn(existingPrisoner)
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(false)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerDifferenceService, never()).handleDifferences(any(), any(), any(), any())
      verify(prisonerRepository, never()).save(any(), any())
      verify(telemetryClient).trackEvent(
        eq(PRISONER_OPENSEARCH_NO_CHANGE.name),
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
    fun `will call prisoner difference to handle differences`() {
      val existingPrisoner = Prisoner()
      whenever(prisonerRepository.get(any(), any())).thenReturn(existingPrisoner)
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      service.reindex(booking, listOf(GREEN), "event")

      verify(prisonerDifferenceService).handleDifferences(
        eq(existingPrisoner),
        eq(booking),
        check {
          assertThat(it.prisonerNumber).isEqualTo(booking.offenderNo)
        },
        eq("event"),
      )
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

      service.reindex(prisonBooking, listOf(GREEN), "event")

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.reindex(noLivingUnitBooking, listOf(GREEN), "event")

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

      service.reindex(outsidePrisoner, listOf(GREEN), "event")

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }

    @Test
    fun `will throw exception if restricted patients service call fails, but still save prisoner`() {
      val outsidePrisoner = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "OUT",
          locationId = 1,
          description = "Outside",
          agencyName = "Outside Prison",
        ),
      )
      whenever(restrictedPatientService.getRestrictedPatient(any())).thenThrow(RuntimeException("not today thank you"))
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)

      assertThatThrownBy { service.reindex(outsidePrisoner, listOf(GREEN), "event") }.hasMessage("not today thank you")

      verify(prisonerRepository, times(1)).save(isA(), isA())
    }

    @Test
    fun `will not call incentives if no booking id`() {
      val noBookingId = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.reindex(noBookingId, listOf(GREEN), "event")

      verifyNoInteractions(incentivesService)
    }

    @Test
    fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.reindex(bookingIdBooking, listOf(GREEN), "event")

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    fun `will throw exception if incentives service call fails, but still save prisoner`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)

      assertThatThrownBy { service.reindex(booking, listOf(GREEN), "event") }.hasMessage("not today thank you")

      verify(prisonerRepository, times(1)).save(isA(), isA())
    }
  }

  @Nested
  inner class ReindexUpdate {
    private val booking = OffenderBookingBuilder().anOffenderBooking()
    private val prisonerNumber = booking.offenderNo
    private val prisonerDocumentSummary =
      PrisonerDocumentSummary(
        prisonerNumber,
        Prisoner().apply { bookingId = booking.bookingId.toString() },
        sequenceNumber = 0,
        primaryTerm = 0,
      )

    @Test
    fun `will save prisoner to RED index`() {
      whenever(prisonerRepository.getSummary(any(), eq(RED))).thenReturn(prisonerDocumentSummary)
      service.reindexUpdate(booking, "event")

      verify(prisonerRepository, times(1)).updatePrisoner(isA(), isA(), eq(RED), isA())
    }

    @Test
    fun `will create prisoner if not present`() {
      whenever(prisonerRepository.getSummary(any(), eq(RED))).thenReturn(null)
      whenever(prisonerRepository.get(any(), eq(listOf(RED)))).thenReturn(Prisoner())
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      service.reindexUpdate(booking, "event")

      verify(prisonerRepository, times(1)).save(isA(), isA())
    }

    @Test
    fun `will not save prisoner if no changes`() {
      whenever(prisonerRepository.getSummary(any(), eq(RED))).thenReturn(prisonerDocumentSummary)
      whenever(prisonerRepository.updatePrisoner(any(), any(), eq(RED), isA())).thenReturn(false)
      service.reindexUpdate(booking, "event")

      verify(prisonerDifferenceService, never()).handleDifferences(any(), any(), any(), any())
      verify(telemetryClient).trackEvent(
        eq(TelemetryEvents.RED_PRISONER_OPENSEARCH_NO_CHANGE.name),
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
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(prisonerRepository.getSummary(any(), eq(RED))).thenReturn(prisonerDocumentSummary)
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      service.reindexUpdate(booking, "event")

      verifyNoInteractions(prisonerDifferenceService)
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

      whenever(prisonerRepository.getSummary(any(), eq(RED)))
        .thenReturn(prisonerDocumentSummary, prisonerDocumentSummaryAfterUpdate)
      whenever(
        prisonerRepository.updatePrisoner(
          eq(prisonerNumber),
          any(),
          eq(RED),
          eq(prisonerDocumentSummary),
        ),
      )
        .thenReturn(true)
      whenever(incentivesService.getCurrentIncentive(changedBookingId)).thenReturn(newIncentive)

      service.reindexUpdate(booking.copy(bookingId = changedBookingId), "event")

      verify(prisonerRepository, times(1)).updateIncentive(
        eq(prisonerNumber),
        eq(newIncentive.toCurrentIncentive()),
        eq(RED),
        eq(prisonerDocumentSummaryAfterUpdate),
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

    @Test
    fun `will save incentive to current index`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      service.reindexIncentive(prisonerNumber, GREEN, "event")

      verify(prisonerRepository).updateIncentive(
        eq(prisonerNumber),
        isA(),
        check { assertThat(it).isEqualTo(GREEN) },
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create telemetry`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any(), any())).thenReturn(true)
      service.reindexIncentive(prisonerNumber, GREEN, "event")

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
    fun `will not save prisoner if no changes`() {
      whenever(prisonerRepository.getSummary(eq(prisonerNumber), any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq(prisonerNumber), any(), any(), any())).thenReturn(false)
      service.reindexIncentive(prisonerNumber, GREEN, "event")

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
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(null)

      service.reindexIncentive(prisonerNumber, GREEN, "event")

      verifyNoInteractions(incentivesService)
      verify(prisonerRepository, never()).updateIncentive(any(), any(), any(), any())
    }

    @Test
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      whenever(incentivesService.getCurrentIncentive(bookingId)).thenReturn(newIncentive)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any(), any())).thenReturn(true)
      service.reindexIncentive(prisonerNumber, GREEN, "event")

      verifyNoInteractions(prisonerDifferenceService)
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

    @Test
    fun `will call restricted patients and save RP to current index if prisoner is outside`() {
      whenever(prisonerRepository.getSummary(prisonerNumber, RED)).thenReturn(prisonerDocumentSummary)
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
          any(),
        ),
      ).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, RED, "event")

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
        eq(RED),
        eq(prisonerDocumentSummary),
      )
    }

    @Test
    fun `will create telemetry`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
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
          any(),
        ),
      ).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, GREEN, "event")

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
      whenever(prisonerRepository.getSummary(eq(prisonerNumber), any())).thenReturn(prisonerDocumentSummary)
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
          any(),
        ),
      ).thenReturn(false)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, GREEN, "event")

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
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(null)

      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, GREEN, "event")

      verifyNoInteractions(incentivesService)
      verify(prisonerRepository, never()).updateIncentive(any(), any(), any(), any())
    }

    @Test
    fun `will NOT call prisoner difference to handle differences`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      whenever(restrictedPatientService.getRestrictedPatient(prisonerNumber)).thenReturn(newRestrictedPatient)
      whenever(prisonerRepository.updateIncentive(eq("A1234AA"), any(), any(), any())).thenReturn(true)
      service.reindexRestrictedPatient(prisonerNumber, outsidePrisoner, GREEN, "event")

      verifyNoInteractions(prisonerDifferenceService)
    }

    @Test
    fun `will not call restricted patients if prisoner in a prison`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      val prisonBooking = OffenderBookingBuilder().anOffenderBooking(offenderNo = prisonerNumber).copy(
        assignedLivingUnit = AssignedLivingUnit(
          agencyId = "MDI",
          locationId = 1,
          description = "Moorland",
          agencyName = "Moorland",
        ),
      )

      service.reindexRestrictedPatient(prisonerNumber, prisonBooking, GREEN, "event")

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      whenever(prisonerRepository.getSummary(any(), any())).thenReturn(prisonerDocumentSummary)
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking(offenderNo = prisonerNumber).copy(
        assignedLivingUnit = null,
      )

      service.reindexRestrictedPatient(noLivingUnitBooking.offenderNo, noLivingUnitBooking, GREEN, "event")

      verifyNoInteractions(restrictedPatientService)
    }
  }

  @Nested
  inner class Index {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    fun `will save prisoner to repository`() {
      service.index(booking, GREEN)

      verify(prisonerRepository, times(2)).save(isA(), isA())
    }

    @Test
    fun `will save prisoner to current index`() {
      service.index(booking, GREEN)

      verify(prisonerRepository).save(isA(), eq(RED))
      verify(prisonerRepository).save(isA(), eq(GREEN))
    }

    @Test
    fun `will not send telemetry event for insert`() {
      service.index(booking, GREEN)

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

      service.index(prisonBooking, GREEN)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.index(noLivingUnitBooking, GREEN)

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

      service.index(outsideBooking, GREEN)

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

      assertThatThrownBy { service.index(outsideBooking, GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }

    @Test
    fun `will not call incentives if no booking id`() {
      val noBookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = null,
      )

      service.index(noBookingIdBooking, GREEN)

      verifyNoInteractions(incentivesService)
    }

    @Test
    fun `will call incentives service if there is a booking`() {
      val bookingIdBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        bookingId = 12345L,
      )

      service.index(bookingIdBooking, GREEN)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    fun `will throw exception if incentives service call fails`() {
      whenever(incentivesService.getCurrentIncentive(any())).thenThrow(RuntimeException("not today thank you"))

      assertThatThrownBy { service.index(booking, GREEN) }.hasMessage("not today thank you")

      verifyNoInteractions(prisonerRepository)
    }
  }

  @Nested
  inner class CompareAndMaybeIndex {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @BeforeEach
    fun setUp() {
      whenever(prisonerRepository.save(any(), any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `will do nothing if no differences found`() {
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(false)
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, Result.success(null), Result.success(null), listOf(GREEN), LABEL)

      verify(prisonerRepository, never()).save(any(), any())
      verify(prisonerDifferenceService).hasChanged(any(), any())
      verifyNoMoreInteractions(prisonerDifferenceService)
    }

    @Test
    fun `will report differences if found`() {
      whenever(prisonerDifferenceService.hasChanged(any(), any())).thenReturn(true)
      whenever(prisonerRepository.get(any(), any())).thenReturn(Prisoner())

      service.compareAndMaybeIndex(booking, Result.success(null), Result.success(null), listOf(GREEN), LABEL)

      verify(prisonerDifferenceService).reportDiffTelemetry(any(), any(), eq(LABEL))
      verify(prisonerRepository).save(any(), eq(GREEN))
      verify(prisonerDifferenceService).handleDifferences(any(), any(), any(), any())
    }
  }

  @Nested
  inner class GetDomainData {
    private val booking = OffenderBookingBuilder().anOffenderBooking()

    @Test
    fun `will get incentive level if booking present`() {
      service.getDomainData(booking)

      verify(incentivesService).getCurrentIncentive(12345L)
    }

    @Test
    fun `will not get incentive if there is no booking`() {
      service.getDomainData(OffenderBookingBuilder().anOffenderBooking(null))

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

      service.getDomainData(prisonBooking)

      verifyNoInteractions(restrictedPatientService)
    }

    @Test
    fun `will not call restricted patients if assigned living unit not set`() {
      val noLivingUnitBooking = OffenderBookingBuilder().anOffenderBooking().copy(
        assignedLivingUnit = null,
      )

      service.getDomainData(noLivingUnitBooking)

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

      service.getDomainData(outsidePrisoner)

      verify(restrictedPatientService).getRestrictedPatient("A1234AA")
    }
  }

  @Nested
  inner class Delete {
    @Test
    fun `will raise a telemetry event`() {
      service.delete("ABC123D")

      verify(telemetryClient).trackEvent(
        TelemetryEvents.PRISONER_REMOVED.name,
        mapOf("prisonerNumber" to "ABC123D"),
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
