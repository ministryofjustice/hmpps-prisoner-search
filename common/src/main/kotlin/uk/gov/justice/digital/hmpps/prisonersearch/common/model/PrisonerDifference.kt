package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import org.apache.commons.lang3.builder.DiffBuilder
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.ToStringStyle
import kotlin.reflect.full.findAnnotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DiffableProperty(val type: DiffCategory)

enum class DiffCategory {
  IDENTIFIERS,
  PERSONAL_DETAILS,
  ALERTS,
  STATUS,
  LOCATION,
  SENTENCE,
  RESTRICTED_PATIENT,
  INCENTIVE_LEVEL,
  PHYSICAL_DETAILS,
  CONTACT_DETAILS,
}

internal fun getDiffResult(prisoner: Prisoner, other: Prisoner): DiffResult<Prisoner> = DiffBuilder(prisoner, other, ToStringStyle.JSON_STYLE).apply {
  Prisoner::class.members
    .filter { it.findAnnotations<DiffableProperty>().isNotEmpty() }
    .forEach { property -> append(property.name, property.call(prisoner), property.call(other)) }
}.build()

internal fun getDiffResult(incentive: CurrentIncentive, other: CurrentIncentive): DiffResult<CurrentIncentive> = DiffBuilder(incentive, other, ToStringStyle.JSON_STYLE).apply {
  CurrentIncentive::class.members
    .filter { it.findAnnotations<DiffableProperty>().isNotEmpty() }
    .forEach { property -> append(property.name, property.call(incentive), property.call(other)) }
}.build()
