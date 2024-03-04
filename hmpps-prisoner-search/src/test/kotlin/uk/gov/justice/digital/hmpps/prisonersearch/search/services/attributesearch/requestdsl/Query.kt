package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.TypeMatcher

@DslMarker
annotation class QueryDslMarker

/**
 * Part of the [RequestDsl].
 *
 * To create a new [Query], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       ...
 *     }
 *   }
 * ```
 *
 * For the default values of parameters see [QueryBuilder].
 */
@QueryDslMarker
interface QueryDsl {

  /**
   * The type of join to use when combining the matchers
   */
  var joinType: JoinType

  /**
   * A string matcher to match a String attribute. See [StringMatcherBuilder] for more details.
   */
  @StringMatcherDslMarker
  fun stringMatcher(stringAssertion: StringAssertion): StringMatcher
}

class QueryBuilder : QueryDsl {
  override var joinType = JoinType.AND
  private val matchers = mutableListOf<TypeMatcher<*>>()

  override fun stringMatcher(stringAssertion: StringAssertion) =
    StringMatcherBuilder(stringAssertion)
      .build()
      .also { matchers += it }

  fun build(): Query = Query(joinType, matchers)
}
