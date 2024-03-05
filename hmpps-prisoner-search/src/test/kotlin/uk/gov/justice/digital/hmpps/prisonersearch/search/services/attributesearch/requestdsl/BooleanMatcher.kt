package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher

@DslMarker
annotation class BooleanMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [BooleanMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       booleanMatcher("attribute" IS false)
 *       ...
 *     }
 *   }
 * ```
 */
@BooleanMatcherDslMarker
interface BooleanMatcherDsl

class BooleanMatcherBuilder(private val booleanAssertion: BooleanAssertion) : BooleanMatcherDsl {

  fun build(): BooleanMatcher = with(booleanAssertion) { BooleanMatcher(attribute, condition) }
}

class BooleanAssertion(
  val attribute: String,
  val condition: Boolean,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.IS(condition: Boolean) = BooleanAssertion(this, condition)
