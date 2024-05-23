package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import kotlin.reflect.KClass

/**
 * An attribute is a property found on the Prisoner record (or its descendents) that can be searched for because it has a matcher for its type.
 */
class Attribute(
  val type: KClass<*>,
  val openSearchName: String,
  val isNested: Boolean = false,
  val isFuzzy: Boolean = false,
)

fun String.isFuzzyAttribute() = FUZZY_ATTRIBUTES.contains(this)

// Fuzzy attributes use OpenSearch's fuzzy search feature to allow for minor spelling mistakes and other errors.
private val FUZZY_ATTRIBUTES = listOf(
  "firstName",
  "middleNames",
  "lastName",
  "prisonName",
  "mostSeriousOffence",
  "locationDescription",
  "dischargeHospitalDescription",
  "dischargeDetails",
  "tattoos.comment",
  "scars.comment",
  "marks.comment",
  "aliases.firstName",
  "aliases.middleNames",
  "aliases.lastName",
  "allConvictedOffences.offenceDescription",
)
