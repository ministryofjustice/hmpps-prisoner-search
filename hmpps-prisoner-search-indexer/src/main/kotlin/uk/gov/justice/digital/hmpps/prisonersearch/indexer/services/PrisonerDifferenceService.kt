package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.Diffable
import org.apache.commons.lang3.builder.ToStringStyle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.DigestUtils
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffableProperty
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_MISSING
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_REPORTED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_CREATED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.PRISONER_UPDATED_NO_DIFFERENCES
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesLabel
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import kotlin.reflect.full.findAnnotations
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferences as PrisonerDiffs

@Service
class PrisonerDifferenceService(
  private val telemetryClient: TelemetryClient,
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val diffProperties: DiffProperties,
  private val objectMapper: ObjectMapper,
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

  fun reportDifferencesDetails(previousPrisonerSnapshot: Prisoner?, prisoner: Prisoner) = if (hasChanged(previousPrisonerSnapshot, prisoner)) {
    reportDiffTelemetryDetails(previousPrisonerSnapshot, prisoner)
  } else {
    emptyList()
  }

  fun hasChanged(previousSnapshot: Any?, current: Any): Boolean = hash(previousSnapshot) != hash(current)

  fun hash(value: Any?) = value?.run {
    objectMapper.writeValueAsString(this)
      .toByteArray()
      .let {
        Base64.encodeBase64String(DigestUtils.md5Digest(it))
      }
  }

  internal fun <T : Diffable<T>> generateDiffTelemetry(
    previousSnapshot: T?,
    prisonerNumber: String,
    bookingId: Long?,
    current: T,
    eventType: String,
  ) {
    runCatching {
      previousSnapshot?.also {
        getDifferencesByCategory(it, current).run {
          // TODO: this is used by domains too: customise the telemetry event name?
          raiseDifferencesTelemetry(prisonerNumber, bookingId, eventType, this)
        }
      } ?: telemetryClient.trackPrisonerEvent(PRISONER_CREATED, prisonerNumber, bookingId, eventType)
    }.onFailure {
      log.error("Prisoner difference telemetry failed with error", it)
    }
  }

  fun reportDiffTelemetry(
    previousPrisonerSnapshot: Prisoner?,
    prisoner: Prisoner,
    label: PrisonerDifferencesLabel,
  ) {
    previousPrisonerSnapshot?.also { _ ->
      getDifferencesByCategory(previousPrisonerSnapshot, prisoner).takeIf { it.isNotEmpty() }?.also {
        // we store a summary of the differences in app insights
        telemetryClient.trackEvent(
          DIFFERENCE_REPORTED,
          mapOf(
            "prisonerNumber" to previousPrisonerSnapshot.prisonerNumber!!,
            "categoriesChanged" to it.keys.map { it.name }.toList().sorted().toString(),
            "label" to label.toString(),
          ),
        )
      }
      // and the sensitive full differences in our postgres database
      reportDiffTelemetryDetails(previousPrisonerSnapshot, prisoner).takeIf { it.isNotEmpty() }?.also {
        prisonerDifferencesRepository.save(
          PrisonerDiffs(nomsNumber = prisoner.prisonerNumber!!, differences = it.toString(), label = label),
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

  internal fun <T : Diffable<T>> generateDiffEvent(
    previousSnapshot: T?,
    prisonerNumber: String,
    current: T,
    red: Boolean,
  ) {
    if (!diffProperties.events) return
    previousSnapshot?.also {
      getDifferencesByCategory(it, current)
        .takeIf { it.isNotEmpty() }
        ?.also { domainEventEmitter.emitPrisonerDifferenceEvent(prisonerNumber, it, red) }
    } ?: domainEventEmitter.emitPrisonerCreatedEvent(prisonerNumber, red)
  }

  @Suppress("UNCHECKED_CAST")
  internal fun <T : Diffable<T>> getDifferencesByCategory(prisoner: Diffable<T>, other: T): PrisonerDifferences = prisoner.diff(other).let { diffResult ->
    propertiesByDiffCategory.mapValues { properties ->
      val diffs = diffResult.diffs as List<Diff<T>>
      diffs.filter { diff -> properties.value.contains(diff.fieldName) }
        .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
    }
  }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }

  private fun raiseDifferencesTelemetry(
    offenderNo: String,
    bookingId: Long?,
    eventType: String,
    differences: PrisonerDifferences,
  ) = if (differences.isEmpty()) {
    // we've detected a change in the hash for the prisoner, but no differences are recorded
    telemetryClient.trackPrisonerEvent(
      PRISONER_UPDATED_NO_DIFFERENCES,
      prisonerNumber = offenderNo,
      bookingId = bookingId,
      eventType = eventType,
    )
  } else {
    telemetryClient.trackEvent(
      PRISONER_UPDATED,
      mapOf(
        "prisonerNumber" to offenderNo,
        "bookingId" to (bookingId?.toString() ?: "not set"),
        "event" to eventType,
        "categoriesChanged" to differences.keys.map { it.name }.toList().sorted().toString(),
      ),
    )
  }
}

data class Difference(val property: String, val categoryChanged: DiffCategory, val oldValue: Any?, val newValue: Any?)

typealias PrisonerDifferences = Map<DiffCategory, List<Difference>>
