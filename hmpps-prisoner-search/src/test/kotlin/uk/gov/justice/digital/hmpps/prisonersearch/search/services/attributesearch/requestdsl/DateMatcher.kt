package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import java.time.LocalDate

@DslMarker
annotation class DateMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [DateMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       dateMatcher("attribute" GTE "2022-01-01" AND_LTE "2022-12-31")
 *       ...
 *     }
 *   }
 * ```
 */
@DateMatcherDslMarker
interface DateMatcherDsl

class DateMatcherBuilder(private val DateAssertion: DateAssertion) : DateMatcherDsl {

  fun build(): DateMatcher = with(DateAssertion) { DateMatcher(attribute, minValue, minInclusive, maxValue, maxInclusive) }
}

class DateAssertion(
  val attribute: String,
  val minValue: LocalDate? = null,
  val minInclusive: Boolean = true,
  val maxValue: LocalDate? = null,
  val maxInclusive: Boolean = true,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.EQ(minValue: String) = DateAssertion(this, minValue = minValue.asDate(), maxValue = minValue.asDate())

@Suppress("ktlint:standard:function-naming")
internal infix fun String.GTE(minValue: String) = DateAssertion(this, minValue = minValue.asDate())

@Suppress("ktlint:standard:function-naming")
internal infix fun String.GT(minValue: String) = DateAssertion(this, minValue = minValue.asDate(), minInclusive = false)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.LTE(maxValue: String) = DateAssertion(this, maxValue = maxValue.asDate())

@Suppress("ktlint:standard:function-naming")
internal infix fun String.LT(maxValue: String) = DateAssertion(this, maxValue = maxValue.asDate(), maxInclusive = false)

@Suppress("ktlint:standard:function-naming")
internal infix fun DateAssertion.AND_LTE(maxValue: String) = DateAssertion(
  this.attribute,
  minValue = this.minValue,
  minInclusive = this.minInclusive,
  maxValue = maxValue.asDate(),
  maxInclusive = true,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun DateAssertion.AND_LT(maxValue: String) = DateAssertion(
  this.attribute,
  minValue = this.minValue,
  minInclusive = this.minInclusive,
  maxValue = maxValue.asDate(),
  maxInclusive = false,
)

private fun String.asDate() = LocalDate.parse(this)
