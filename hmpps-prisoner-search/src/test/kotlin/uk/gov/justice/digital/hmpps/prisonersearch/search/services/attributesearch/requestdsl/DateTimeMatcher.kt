package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import java.time.LocalDateTime

@DslMarker
annotation class DateTimeMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [DateTimeMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       dateTimeMatcher("attribute" LT LocalDateTime.now())
 *       ...
 *     }
 *   }
 * ```
 */
@DateTimeMatcherDslMarker
interface DateTimeMatcherDsl

class DateTimeMatcherBuilder(private val dateTimeAssertion: DateTimeAssertion) : DateTimeMatcherDsl {

  fun build(): DateTimeMatcher = with(dateTimeAssertion) { DateTimeMatcher(attribute, minValue, maxValue) }
}

class DateTimeAssertion(
  val attribute: String,
  val minValue: LocalDateTime? = null,
  val maxValue: LocalDateTime? = null,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.GT(minValue: LocalDateTime) = DateTimeAssertion(this, minValue = minValue)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.LT(maxValue: LocalDateTime) = DateTimeAssertion(this, maxValue = maxValue)

@Suppress("ktlint:standard:function-naming")
internal infix fun DateTimeAssertion.AND_LT(maxValue: LocalDateTime) = DateTimeAssertion(this.attribute, this.minValue, maxValue)
