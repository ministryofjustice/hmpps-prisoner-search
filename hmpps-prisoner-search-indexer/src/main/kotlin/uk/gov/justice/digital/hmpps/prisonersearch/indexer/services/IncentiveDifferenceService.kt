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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffableProperty
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.IncentiveHashRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import java.time.Instant
import kotlin.reflect.full.findAnnotations

@Service
class IncentiveDifferenceService(
  private val telemetryClient: TelemetryClient,
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val diffProperties: DiffProperties,
  private val incentiveHashRepository: IncentiveHashRepository,
  private val objectMapper: ObjectMapper,
//  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
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

  @Transactional
  fun handleDifferences(
    prisonerNumber: String,
    bookingId: Long,
    previousIncentiveSnapshot: CurrentIncentive?,
    incentive: CurrentIncentive,
    eventType: String,
  ) {
    hash(incentive)!!.run {
      takeIf { updateDbHash(prisonerNumber, it) }?.run {
        generateDiffEvent(previousIncentiveSnapshot, prisonerNumber, incentive)
        generateDiffTelemetry(previousIncentiveSnapshot, prisonerNumber, bookingId, incentive, eventType)
      } ?: raiseDatabaseHashUnchangedTelemetry(prisonerNumber, bookingId, eventType)
    }
  }

  fun incentiveHasChanged(previousSnapshot: CurrentIncentive?, incentive: CurrentIncentive): Boolean =
    hash(previousSnapshot) != hash(incentive)

  fun updateDbHash(nomsNumber: String, hash: String) =
    // upsertIfChanged returns the number of records altered, so > 0 means that we have changed something
    incentiveHashRepository.upsertIfChanged(nomsNumber, hash, Instant.now()) > 0

  private fun hash(value: Any?) =
    value?.run {
      objectMapper.writeValueAsString(this)
        .toByteArray()
        .let {
          Base64.encodeBase64String(DigestUtils.md5Digest(it))
        }
    }

  internal fun generateDiffTelemetry(
    previousSnapshot: CurrentIncentive?,
    prisonerNumber: String,
    bookingId: Long,
    incentive: CurrentIncentive,
    eventType: String,
  ) {
    runCatching {
      previousSnapshot?.also {
        getDifferencesByCategory(it, incentive).run {
          raiseDifferencesTelemetry(prisonerNumber, bookingId, eventType, this)
        }
      } ?: raiseCreatedTelemetry(prisonerNumber, bookingId, eventType)
    }.onFailure {
      log.error("Prisoner difference telemetry failed with error", it)
    }
  }

  internal fun generateDiffEvent(
    previousSnapshot: CurrentIncentive?,
    prisonerNumber: String,
    current: CurrentIncentive,
  ) {
    if (!diffProperties.events) return
    previousSnapshot?.also {
      getDifferencesByCategory(it, current)
        .takeIf { it.isNotEmpty() }
        ?.also { domainEventEmitter.emitPrisonerDifferenceEvent(prisonerNumber, it) }
    } ?: domainEventEmitter.emitPrisonerCreatedEvent(prisonerNumber)
  }

  @Suppress("UNCHECKED_CAST")
  internal fun getDifferencesByCategory(existing: CurrentIncentive, other: CurrentIncentive): PrisonerDifferences =
    existing.diff(other).let { diffResult ->
      propertiesByDiffCategory.mapValues { properties ->
        val diffs = diffResult.diffs as List<Diff<Prisoner>>
        diffs.filter { diff -> properties.value.contains(diff.fieldName) }
          .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
      }
    }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }

  private fun raiseDifferencesTelemetry(
    offenderNo: String,
    bookingId: Long?,
    eventType: String,
    differences: PrisonerDifferences,
  ) =
    if (differences.isEmpty()) {
      // we've detected a change in the hash for the prisoner, but no differences are recorded
      telemetryClient.trackPrisonerEvent(
        TelemetryEvents.INCENTIVE_UPDATED_NO_DIFFERENCES,
        prisonerNumber = offenderNo,
        bookingId = bookingId,
        eventType = eventType,
      )
    } else {
      telemetryClient.trackEvent(
        TelemetryEvents.INCENTIVE_UPDATED,
        mapOf(
          "prisonerNumber" to offenderNo,
          "bookingId" to (bookingId?.toString() ?: "not set"),
          "event" to eventType,
          "categoriesChanged" to differences.keys.map { it.name }.toList().sorted().toString(),
        ),
      )
    }

  private fun raiseDatabaseHashUnchangedTelemetry(
    offenderNo: String,
    bookingId: Long?,
    eventType: String,
  ) =
    // the prisoner hash stored in the database is unchanged
    telemetryClient.trackPrisonerEvent(
      TelemetryEvents.INCENTIVE_DATABASE_NO_CHANGE,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      eventType = eventType,
    )

  private fun raiseCreatedTelemetry(
    offenderNo: String,
    bookingId: Long?,
    eventType: String,
  ) =
    telemetryClient.trackPrisonerEvent(
      TelemetryEvents.INCENTIVE_CREATED,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      eventType = eventType,
    )
}
