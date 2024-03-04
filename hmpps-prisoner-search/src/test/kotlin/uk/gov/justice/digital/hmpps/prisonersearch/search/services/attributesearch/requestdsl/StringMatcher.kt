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
 *       stringMatcher("attribute" `is` "value")
 *       ...
 *     }
 *   }
 * ```
 */
@StringMatcherDslMarker
interface StringMatcherDsl

class StringMatcherBuilder(private val stringAssertion: StringAssertion) : StringMatcherDsl {

  fun build(): StringMatcher = with(stringAssertion) { StringMatcher(attribute.toString(), condition, searchTerm) }
}

class StringAssertion(
  val attribute: String,
  val condition: StringCondition,
  val searchTerm: String,
)

@Suppress("ktlint:standard:function-naming")
internal infix fun String.`is`(value: String) = StringAssertion(this, StringCondition.IS, value)
internal infix fun String.isNot(value: String) = StringAssertion(this, StringCondition.IS_NOT, value)
internal infix fun String.contains(value: String) = StringAssertion(this, StringCondition.CONTAINS, value)
