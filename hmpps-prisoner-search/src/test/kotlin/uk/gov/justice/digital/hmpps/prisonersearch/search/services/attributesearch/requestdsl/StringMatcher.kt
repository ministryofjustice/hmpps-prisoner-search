package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher

@DslMarker
annotation class StringMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [StringMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       stringMatcher("attribute" IS "value")
 *       ...
 *     }
 *   }
 * ```
 */
@StringMatcherDslMarker
interface StringMatcherDsl

class StringMatcherBuilder(private val stringAssertion: StringAssertion) : StringMatcherDsl {

  fun build(): StringMatcher = with(stringAssertion) { StringMatcher(attribute, condition, searchTerm) }
}

class StringAssertion(
  val attribute: String,
  val condition: StringCondition,
  val searchTerm: String,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.IS(value: String) = StringAssertion(this, StringCondition.IS, value)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.IS_NOT(value: String) = StringAssertion(this, StringCondition.IS_NOT, value)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.CONTAINS(value: String) = StringAssertion(this, StringCondition.CONTAINS, value)
