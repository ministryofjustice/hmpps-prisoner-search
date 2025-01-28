package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Matcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.PncMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher

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

  /**
   * An integer matcher to match an Int attribute. See [IntMatcherBuilder] for more details.
   */
  @IntMatcherDslMarker
  fun intMatcher(intAssertion: IntAssertion): IntMatcher

  /**
   * A boolean matcher to match a Boolean attribute. See [BooleanMatcherBuilder] for more details.
   */
  @BooleanMatcherDslMarker
  fun booleanMatcher(booleanAssertion: BooleanAssertion): BooleanMatcher

  /**
   * A date/time matcher to match a LocalDateTime attribute. See [DateTimeMatcherBuilder] for more details.
   */
  @DateTimeMatcherDslMarker
  fun dateTimeMatcher(dateTimeAssertion: DateTimeAssertion): DateTimeMatcher

  /**
   * A date matcher to match a LocalDate attribute. See [DateMatcherBuilder] for more details.
   */
  @DateMatcherDslMarker
  fun dateMatcher(dateAssertion: DateAssertion): DateMatcher

  /**
   * A PNC matcher. See [PncMatcher] for more details.
   */
  @PncMatcherDslMarker
  fun pncMatcher(pncNumber: String): PncMatcher

  /**
   * A sub-query to join with or without matchers and other sub-queries. See [QueryBuilder] for more details.
   */
  @QueryDslMarker
  fun subQuery(dsl: QueryDsl.() -> Unit): Query
}

class QueryBuilder : QueryDsl {
  override var joinType = JoinType.AND
  private val matchers = mutableListOf<Matcher>()
  private val subQueries = mutableListOf<Query>()

  override fun stringMatcher(stringAssertion: StringAssertion) = StringMatcherBuilder(stringAssertion)
    .build()
    .also { matchers += it }

  override fun intMatcher(intAssertion: IntAssertion) = IntMatcherBuilder(intAssertion)
    .build()
    .also { matchers += it }

  override fun booleanMatcher(booleanAssertion: BooleanAssertion) = BooleanMatcherBuilder(booleanAssertion)
    .build()
    .also { matchers += it }

  override fun dateTimeMatcher(dateTimeAssertion: DateTimeAssertion): DateTimeMatcher = DateTimeMatcherBuilder(dateTimeAssertion)
    .build()
    .also { matchers += it }

  override fun dateMatcher(dateAssertion: DateAssertion): DateMatcher = DateMatcherBuilder(dateAssertion)
    .build()
    .also { matchers += it }

  override fun pncMatcher(pncNumber: String): PncMatcher = PncMatcher(pncNumber)
    .also { matchers += it }

  override fun subQuery(dsl: QueryDsl.() -> Unit): Query = QueryBuilder()
    .apply(dsl)
    .build()
    .also { subQueries += it }

  fun build(): Query = Query(joinType, matchers, subQueries)
}
