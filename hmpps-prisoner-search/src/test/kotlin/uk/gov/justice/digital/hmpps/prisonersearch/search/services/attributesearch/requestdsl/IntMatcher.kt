package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntMatcher

@DslMarker
annotation class IntMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [IntMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       intMatcher("attribute" GTE 150 AND_LTE 180)
 *       ...
 *     }
 *   }
 * ```
 */
@IntMatcherDslMarker
interface IntMatcherDsl

class IntMatcherBuilder(private val intAssertion: IntAssertion) : IntMatcherDsl {

  fun build(): IntMatcher = with(intAssertion) { IntMatcher(attribute, minValue, minInclusive, maxValue, maxInclusive) }
}

class IntAssertion(
  val attribute: String,
  val minValue: Int? = null,
  val minInclusive: Boolean = true,
  val maxValue: Int? = null,
  val maxInclusive: Boolean = true,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.EQ(minValue: Int) = IntAssertion(this, minValue = minValue, maxValue = minValue)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.GTE(minValue: Int) = IntAssertion(this, minValue = minValue)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.GT(minValue: Int) = IntAssertion(this, minValue = minValue, minInclusive = false)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.LTE(maxValue: Int) = IntAssertion(this, maxValue = maxValue)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.LT(maxValue: Int) = IntAssertion(this, maxValue = maxValue, maxInclusive = false)

@Suppress("ktlint:standard:function-naming")
internal infix fun IntAssertion.AND_LTE(maxValue: Int) = IntAssertion(
  this.attribute,
  minValue = this.minValue,
  minInclusive = this.minInclusive,
  maxValue = maxValue,
  maxInclusive = true,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun IntAssertion.AND_LT(maxValue: Int) = IntAssertion(
  this.attribute,
  minValue = this.minValue,
  minInclusive = this.minInclusive,
  maxValue = maxValue,
  maxInclusive = false,
)
