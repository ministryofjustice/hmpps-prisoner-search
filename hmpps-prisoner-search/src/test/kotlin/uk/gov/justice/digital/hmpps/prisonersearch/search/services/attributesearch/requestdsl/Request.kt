package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType.AND
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query

@DslMarker
annotation class RequestDslMarker

/**
 * The [RequestDsl] for creating [AttributeSearchRequest] instances.
 *
 * To create an [AttributeSearchRequest], use the [RequestDsl.Companion.invoke] function, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     ...
 *   }
 * ```
 */
@RequestDslMarker
interface RequestDsl {

  /**
   * The type of join to use when combining the queries
   */
  var joinType: JoinType

  /**
   * A query to search for attributes. See [QueryDsl] and [QueryBuilder] for more details.
   */
  @QueryDslMarker
  fun query(
    dsl: QueryDsl.() -> Unit = {},
  ): Query

  companion object {
    operator fun invoke(dsl: RequestDsl.() -> Unit): AttributeSearchRequest =
      RequestBuilder()
        .apply(dsl)
        .build()
  }
}

class RequestBuilder : RequestDsl {
  override var joinType: JoinType = AND
  private val queries = mutableListOf<Query>()

  override fun query(dsl: QueryDsl.() -> Unit) =
    QueryBuilder()
      .apply(dsl)
      .build()
      .also { queries += it }

  fun build(): AttributeSearchRequest = AttributeSearchRequest(joinType, queries)
}
