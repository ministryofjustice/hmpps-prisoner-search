package uk.gov.justice.digital.hmpps.prisonersearch.indexer.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.builder.Diff
import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.Diffable
import org.apache.commons.lang3.builder.ToStringStyle
import org.springframework.stereotype.Service
import org.springframework.util.DigestUtils
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffCategory
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.DiffableProperty
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.DiffProperties
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_MISSING
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.TelemetryEvents.DIFFERENCE_REPORTED
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.config.trackPrisonerEvent
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferencesRepository
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.events.HmppsDomainEventEmitter
import kotlin.reflect.full.findAnnotations
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository.PrisonerDifferences as PrisonerDiffs

@Service
class PrisonerDifferenceService(
  private val telemetryClient: TelemetryClient,
  private val domainEventEmitter: HmppsDomainEventEmitter,
  private val diffProperties: DiffProperties,
  private val jsonMapper: JsonMapper,
  private val prisonerDifferencesRepository: PrisonerDifferencesRepository,
) {
  private companion object {
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
    jsonMapper.writeValueAsString(this)
      .toByteArray()
      .let {
        Base64.encodeBase64String(DigestUtils.md5Digest(it))
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

  internal fun <T : Diffable<T>> generateDiffEvent(
    previousSnapshot: T?,
    prisonerNumber: String,
    current: T,
  ) {
    if (!diffProperties.events) return
    previousSnapshot?.also {
      getDifferencesByCategory(it, current)
        .takeIf { it.isNotEmpty() }
        ?.also { domainEventEmitter.emitPrisonerDifferenceEvent(prisonerNumber, it) }
    } ?: domainEventEmitter.emitPrisonerCreatedEvent(prisonerNumber)
  }

  internal fun generateAlertDiffEvent(
    previousAlerts: List<PrisonerAlert>?,
    prisonerNumber: String,
    current: List<PrisonerAlert>?,
  ) {
    if (!diffProperties.events) return
    domainEventEmitter.emitPrisonerDifferenceEvent(
      prisonerNumber,
      mapOf(
        DiffCategory.ALERTS to listOf(
          Difference(
            property = "alerts",
            categoryChanged = DiffCategory.ALERTS,
            oldValue = previousAlerts,
            newValue = current,
          ),
        ),
      ),
    )
  }

  @Suppress("UNCHECKED_CAST")
  internal fun <T : Diffable<T>> getDifferencesByCategory(prisoner: Diffable<T>, other: T): PrisonerDifferences = prisoner.diff(other).let { diffResult ->
    propertiesByDiffCategory.mapValues { properties ->
      val diffs = diffResult.diffs as List<Diff<T>>
      diffs.filter { diff -> properties.value.contains(diff.fieldName) }
        .map { diff -> Difference(diff.fieldName, properties.key, diff.left, diff.right) }
    }
  }.filter { differencesByCategory -> differencesByCategory.value.isNotEmpty() }
}

data class Difference(val property: String, val categoryChanged: DiffCategory, val oldValue: Any?, val newValue: Any?)

typealias PrisonerDifferences = Map<DiffCategory, List<Difference>>
