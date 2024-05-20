package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.requestdsl

import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.PncMatcher

@DslMarker
annotation class PncMatcherDslMarker

/**
 * Part of the [QueryDsl] DSL.
 *
 * To create a new instance of [PncMatcher], use the [RequestDsl.Companion.invoke] method, e.g.
 *
 * ```
 *   val request = RequestDsl {
 *     query {
 *       pncMatcher("value")
 *       ...
 *     }
 *   }
 * ```
 */
@PncMatcherDslMarker
interface PncMatcherDsl
