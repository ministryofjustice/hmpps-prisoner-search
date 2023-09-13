package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.DigestUtils
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffableProperty
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_MISSING
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_REPORTED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_CREATED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED_DB_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED_NO_DIFFERENCES
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED_OS_NO_CHANGE
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerHashRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.dto.nomis.OffenderBooking
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.AlertsUpdatedEventService
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.PrisonerMovementsEventService
import java.time.Instant
import kotlin.reflect.full.findAnnotations
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferences as PrisonerDiffs

@Service
class PrisonerDifferenceService(
  private val telemetryClient: TelemetryClient,
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val diffProperties: DiffProperties,
  private val prisonerHashRepository: PrisonerHashRepository,
  private val objectMapper: ObjectMapper,
  private val prisonerMovementsEventService: PrisonerMovementsEventService,
  private val alertsUpdatedEventService: AlertsUpdatedEventService,
  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
) {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val exemptedMethods = listOf("diff", "equals", "toString", "hashCode")
  }

  internal val propertiesByDiffCategory: Map<DiffCategory, List<String>> =
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .groupBy { property -> property.findAnnotations<DiffableProperty>().first().type }
      .mapValues { propertiesByDiffCategory -> propertiesByDiffCategory.value.map { property -> property.name } }

  internal val diffCategoriesByProperty: Map<String, DiffCategory> =
    Prisoner::class.members
      .filter { property -> property.findAnnotations<DiffableProperty>().isNotEmpty() }
      .associate { property -> property.name to property.findAnnotations<DiffableProperty>().first().type }

  @Transactional
  fun handleDifferences(
    previousPrisonerSnapshot: Prisoner?,
    offenderBooking: OffenderBooking,
    prisoner: Prisoner,
  ) {
    prisoner.hash().takeIf { previousPrisonerSnapshot?.hash() != it }
      ?.run {
        takeIf { updateDbHash(offenderBooking.offenderNo, it) }?.run {
          generateDiffEvent(previousPrisonerSnapshot, offenderBooking, prisoner)
          generateDiffTelemetry(previousPrisonerSnapshot, offenderBooking, prisoner)
          prisonerMovementsEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner, offenderBooking)
          alertsUpdatedEventService.generateAnyEvents(previousPrisonerSnapshot, prisoner)
        } ?: raiseDatabaseHashUnchangedTelemetry(offenderBooking.offenderNo)
      } ?: raiseNoDifferencesTelemetry(offenderBooking.offenderNo)
  }

  fun reportDifferencesDetails(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner) =
    if (prisonerHasChanged(previousPrisonerSnapshot, prisoner)) {
      reportDiffTelemetryDetails(previousPrisonerSnapshot, prisoner)
    } else {
      emptyList()
    }

  fun prisonerHasChanged(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner): Boolean =
    previousPrisonerSnapshot?.hash() != prisoner.hash()

  fun updateDbHash(nomsNumber: String, prisonerHash: String) =
    // upsertIfChanged returns the number of records altered, so > 0 means that we have changed something
    prisonerHashRepository.upsertIfChanged(nomsNumber, prisonerHash, Instant.now()) > 0

  private fun Prisoner.hash() =
    objectMapper.writeValueAsString(this)
      .toByteArray()
      .let {
        Base64.encodeBase64String(DigestUtils.md5Digest(it))
      }

  internal fun generateDiffTelemetry(
    previousPrisonerSnapshot: Prisoner?,
    offenderBooking: OffenderBooking,
    prisoner: Prisoner,
  ) {
    runCatching {
      previousPrisonerSnapshot?.also {
        getDifferencesByCategory(it, prisoner).run {
          raiseDifferencesTelemetry(offenderBooking.offenderNo, this)
        }
      } ?: raiseCreatedTelemetry(offenderBooking.offenderNo)
    }.onFailure {
      log.error("Prisoner difference telemetry failed with error", it)
    }
  }

  fun reportDiffTelemetry(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
  ) {
    previousPrisonerSnapshot?.also { _ ->
      getDifferencesByCategory(previousPrisonerSnapshot, prisoner).takeIf { it.isNotEmpty() }?.also {
        // we store a summary of the differences in app insights
        telemetryClient.trackEvent(
          DIFFERENCE_REPORTED,
          mapOf(
            "prisonerNumber" to previousPrisonerSnapshot.prisonerNumber!!,
            "categoriesChanged" to it.keys.map { it.name }.toList().sorted().toString(),
          ),
        )
      }
      // and the sensitive full differences in our postgres database
      reportDiffTelemetryDetails(previousPrisonerSnapshot, prisoner).takeIf { it.isNotEmpty() }?.also {
        prisonerDifferencesRepository.save(
          PrisonerDiffs(nomsNumber = prisoner.prisonerNumber!!, differences = it.toString()),
        )
      }
    }
      ?: telemetryClient.trackPrisonerEvent(DIFFERENCE_MISSING, prisoner.prisonerNumber!!)
  }

  @Suppress("UNCHECKED_CAST")
  fun reportDiffTelemetryDetails(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
  ): List<Diff<Prisoner>> {
    previousPrisonerSnapshot?.also {
      val differences = DiffBuilder(it, prisoner, ToStringStyle.JSON_STYLE).apply {
        Prisoner::class.members
          .filterNot { exemptedMethods.contains(it.name) }
          .forEach { property ->
            append(
              property.name,
              property.call(it),
              property.call(prisoner),
            )
          }
      }.build().diffs
      return differences as List<Diff<Prisoner>>
    }
    return emptyList()
  }

  internal fun generateDiffEvent(
    previousPrisonerSnapshot: Prisoner?,
    offenderBooking: OffenderBooking,
    prisoner: Prisoner,
  ) {
    if (!diffProperties.events) return
    previousPrisonerSnapshot?.also {
      getDifferencesByCategory(it, prisoner)
        .takeIf { it.isNotEmpty() }
        ?.also { domainEventEmitter.emitPrisonerDifferenceEvent(offenderBooking.offenderNo, it) }
    } ?: domainEventEmitter.emitPrisonerCreatedEvent(offenderBooking.offenderNo)
  }

  @Suppress("UNCHECKED_CAST")
  internal fun getDifferencesByCategory(prisoner: Prisoner, other: Prisoner): PrisonerDifferences =
    prisoner.diff(other).let { diffResult ->
      propertiesByDiffCategory.mapValues { properties ->
        val diffs = diffResult.diffs as List<Diff<Prisoner>>
        diffs.filter { diff -> properties.value.contains(diff.fieldName) }
          .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
      }
    }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }

  private fun raiseDifferencesTelemetry(offenderNo: String, differences: PrisonerDifferences) =
    if (differences.isEmpty()) {
      // we've detected a change in the hash for the prisoner, but no differences are recorded
      telemetryClient.trackPrisonerEvent(PRISONER_UPDATED_NO_DIFFERENCES, offenderNo)
    } else {
      telemetryClient.trackEvent(
        PRISONER_UPDATED,
        mapOf(
          "prisonerNumber" to offenderNo,
          "categoriesChanged" to differences.keys.map { it.name }.toList().sorted().toString(),
        ),
      )
    }

  private fun raiseNoDifferencesTelemetry(offenderNo: String) =
    // the prisoner hash from the previous record stored in open search is unchanged
    telemetryClient.trackPrisonerEvent(PRISONER_UPDATED_OS_NO_CHANGE, offenderNo)

  private fun raiseDatabaseHashUnchangedTelemetry(offenderNo: String) =
    // the prisoner hash stored in the database is unchanged
    telemetryClient.trackPrisonerEvent(PRISONER_UPDATED_DB_NO_CHANGE, offenderNo)

  private fun raiseCreatedTelemetry(offenderNo: String) =
    telemetryClient.trackPrisonerEvent(PRISONER_CREATED, offenderNo)
}

data class Difference(val property: String, val categoryChanged: DiffCategory, val oldValue: Any?, val newValue: Any?)

typealias PrisonerDifferences = Map<DiffCategory, List<Difference>>
