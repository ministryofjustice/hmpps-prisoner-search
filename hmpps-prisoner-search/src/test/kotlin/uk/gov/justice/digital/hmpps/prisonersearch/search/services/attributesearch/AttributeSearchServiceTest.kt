package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.opensearch.action.search.SearchResponse
import org.opensearch.search.SearchHits
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.SearchClient
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.AttributeSearchRequest
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.BooleanMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.DateTimeMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.IntMatcher
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.JoinType
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.Query
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringCondition.IS
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api.StringMatcher
import java.time.LocalDate
import java.time.LocalDateTime

class AttributeSearchServiceTest {

  private val telemetryClient = mock<TelemetryClient>()
  private val elasticsearchClient = mock<SearchClient>()
  private val mapper = mock<ObjectMapper>()

  private val service = AttributeSearchService(getAttributes(Prisoner::class), elasticsearchClient, mapper, telemetryClient)

  @BeforeEach
  fun setUp() {
    val searchResponse = mock<SearchResponse>()
    whenever(elasticsearchClient.search(any())).thenReturn(searchResponse)
    whenever(searchResponse.hits).thenReturn(SearchHits.empty())
  }

  @Nested
  inner class ValidateQuery {
    @Test
    fun `should not allow queries with no contents`() {
      val request = AttributeSearchRequest(queries = listOf())

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("empty")
      }
    }

    @Test
    fun `should not allow deep nested queries with no contents`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            subQueries = listOf(),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("empty")
      }
    }

    @Test
    fun `should validate attributes in nested matchers`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("firstName", IS, ""),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("blank")
      }
    }

    @Test
    fun `should validate attributes from a deep nested matcher`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            subQueries = listOf(
              Query(
                matchers = listOf(StringMatcher("firstName", IS, "")),
              ),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("blank")
      }
    }
  }

  @Nested
  inner class Attributes {
    @Test
    fun `should allow simple attribute`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("firstName", IS, "value"),
            ),
          ),
        ),
      )

      assertDoesNotThrow {
        service.search(request)
      }
    }

    @Test
    fun `should not allow unknown attributes`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("unknownAttribute", IS, "value"),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("unknownAttribute")
      }
    }

    @Test
    fun `should allow attributes in lists of objects`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("aliases.firstName", IS, "value"),
            ),
          ),
        ),
      )

      assertDoesNotThrow {
        service.search(request)
      }
    }

    @Test
    fun `should not allow attributes from lists that don't exist`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("aliases.unknown", IS, "value"),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("aliases.unknown")
      }
    }

    @Test
    fun `should allow attributes in nested objects`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("currentIncentive.level.code", IS, "value"),
            ),
          ),
        ),
      )

      assertDoesNotThrow {
        service.search(request)
      }
    }

    @Test
    fun `should not allow attributes from nested objects that don't exist`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("currentIncentive.level.unknown", IS, "value"),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("currentIncentive.level.unknown")
      }
    }
  }

  @Nested
  inner class AttributeTypes {
    @Test
    fun `should not allow a String matcher for non-string attributes`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("heightCentimetres", IS, "value"),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("heightCentimetres").contains("String")
      }
    }

    @Test
    fun `should not allow a Boolean matcher for non-boolean attributes`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              BooleanMatcher("firstName", true),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("Boolean")
      }
    }

    @Test
    fun `should not allow an Integer matcher for non-integer attributes`() {
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              IntMatcher("firstName", minValue = 150),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("Int")
      }
    }

    @Test
    fun `should not allow Date matcher for non-date attributes`() {
      val today = LocalDate.now()
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              DateMatcher("firstName", minValue = today),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("LocalDate")
      }
    }

    @Test
    fun `should not allow DateTime matcher for non-datetime attributes`() {
      val now = LocalDateTime.now()
      val request = AttributeSearchRequest(
        queries = listOf(
          Query(
            matchers = listOf(
              DateTimeMatcher("firstName", minValue = now.minusDays(7)),
            ),
          ),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }.also {
        assertThat(it.message).contains("firstName").contains("LocalDateTime")
      }
    }
  }

  @Nested
  inner class Telemetry {
    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)
    private val now = LocalDateTime.now()

    @Test
    fun `should track invalid query`() {
      val request = AttributeSearchRequest(
        JoinType.AND,
        queries = listOf(
          Query(
            matchers = listOf(
              StringMatcher("firstName", IS, "Brian"),
            ),
          ),
          Query(),
        ),
      )

      assertThrows<AttributeSearchException> {
        service.search(request)
      }
      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearchError"),
        check {
          assertThat(it["query"]).isEqualTo("(firstName = Brian) AND ()")
        },
        isNull(),
      )
    }

    @Test
    fun `should track string query equal`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                StringMatcher("firstName", IS, "John"),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("firstName = John")
        },
        isNull(),
      )
    }

    @Test
    fun `should track string query not equals`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                StringMatcher("firstName", StringCondition.IS_NOT, "John"),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("firstName != John")
        },
        isNull(),
      )
    }

    @Test
    fun `should track string query contains`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                StringMatcher("firstName", StringCondition.CONTAINS, "John"),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("firstName CONTAINS John")
        },
        isNull(),
      )
    }

    @Test
    fun `should track boolean query`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                BooleanMatcher("recall", true),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("recall = true")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query greater than or equal to`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                IntMatcher("heightCentimetres", minValue = 150),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres >= 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query greater than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                IntMatcher("heightCentimetres", minValue = 150, minInclusive = false),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres > 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query less than or equal to`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                IntMatcher("heightCentimetres", maxValue = 150),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres <= 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query less than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                IntMatcher("heightCentimetres", maxValue = 150, maxInclusive = false),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres < 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query equals`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                IntMatcher("heightCentimetres", minValue = 150, maxValue = 150),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("heightCentimetres = 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track int query range`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(IntMatcher("heightCentimetres", minValue = 150, maxValue = 180)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("(heightCentimetres >= 150 AND heightCentimetres <= 180)")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query greater than or equal to`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", minValue = today)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("releaseDate >= $today")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query greater than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", minValue = today, minInclusive = false)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("releaseDate > $today")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query less than or equal to`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", maxValue = today)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("releaseDate <= $today")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query less than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", maxValue = today, maxInclusive = false)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("releaseDate < $today")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query equals`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", minValue = today, maxValue = today)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("releaseDate = $today")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date query range`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateMatcher("releaseDate", minValue = yesterday, maxValue = tomorrow)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("(releaseDate >= $yesterday AND releaseDate <= $tomorrow)")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date time query greater than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateTimeMatcher("currentIncentive.dateTime", minValue = now)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("currentIncentive.dateTime > $now")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date time query less than`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(DateTimeMatcher("currentIncentive.dateTime", maxValue = now)),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("currentIncentive.dateTime < $now")
        },
        isNull(),
      )
    }

    @Test
    fun `should track date time query range`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              matchers = listOf(
                DateTimeMatcher(
                  attribute = "currentIncentive.dateTime",
                  minValue = yesterday.atStartOfDay(),
                  maxValue = tomorrow.atStartOfDay(),
                ),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("(currentIncentive.dateTime > ${yesterday.atStartOfDay()} AND currentIncentive.dateTime < ${tomorrow.atStartOfDay()})")
        },
        isNull(),
      )
    }

    @Test
    fun `should track multiple matchers with and`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              joinType = JoinType.AND,
              matchers = listOf(
                StringMatcher("lastName", IS, "Smith"),
                IntMatcher("heightCentimetres", minValue = 150),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("lastName = Smith AND heightCentimetres >= 150")
        },
        isNull(),
      )
    }

    @Test
    fun `should track multiple matchers with or`() {
      service.search(
        AttributeSearchRequest(
          queries = listOf(
            Query(
              joinType = JoinType.OR,
              matchers = listOf(
                StringMatcher("lastName", IS, "Smith"),
                IntMatcher("heightCentimetres", minValue = 150),
                IntMatcher("shoeSize", minValue = 10, maxValue = 10),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("lastName = Smith OR heightCentimetres >= 150 OR shoeSize = 10")
        },
        isNull(),
      )
    }

    @Test
    fun `should track matchers and sub-queries`() {
      service.search(
        AttributeSearchRequest(
          joinType = JoinType.AND,
          queries = listOf(
            Query(
              matchers = listOf(
                StringMatcher("lastName", IS, "Smith"),
              ),
            ),
            Query(
              joinType = JoinType.OR,
              matchers = listOf(
                DateMatcher("receptionDate", minValue = yesterday),
                DateMatcher("releaseDate", minValue = today),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("(lastName = Smith) AND (receptionDate >= $yesterday OR releaseDate >= $today)")
        },
        isNull(),
      )
    }

    @Test
    fun `should track multiple sub-queries`() {
      service.search(
        AttributeSearchRequest(
          joinType = JoinType.OR,
          queries = listOf(
            Query(
              joinType = JoinType.AND,
              matchers = listOf(
                StringMatcher("firstName", IS, "John"),
                StringMatcher("lastName", IS, "Smith"),
              ),
            ),
            Query(
              joinType = JoinType.AND,
              matchers = listOf(
                StringMatcher("firstName", IS, "John"),
                StringMatcher("lastName", IS, "Jones"),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo("(firstName = John AND lastName = Smith) OR (firstName = John AND lastName = Jones)")
        },
        isNull(),
      )
    }

    @Test
    fun `should track complicated query`() {
      service.search(
        AttributeSearchRequest(
          joinType = JoinType.AND,
          queries = listOf(
            Query(
              matchers = listOf(
                StringMatcher("firstName", IS, "John"),
                StringMatcher("lastName", IS, "Jones"),
              ),
            ),
            Query(
              joinType = JoinType.OR,
              matchers = listOf(
                DateMatcher("releaseDate", minValue = yesterday, maxValue = tomorrow),
                DateMatcher("releaseDate", minValue = today.plusDays(30)),
              ),
            ),
            Query(
              joinType = JoinType.AND,
              matchers = listOf(
                DateMatcher("releaseDate", minValue = today, maxValue = today),
              ),
              subQueries = listOf(
                Query(
                  joinType = JoinType.OR,
                  matchers = listOf(
                    BooleanMatcher("recall", true),
                    BooleanMatcher("indeterminateSentence", false),
                  ),
                ),
              ),
            ),
          ),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("POSAttributeSearch"),
        check {
          assertThat(it["query"]).isEqualTo(
            "(firstName = John AND lastName = Jones) AND ((releaseDate >= $yesterday AND releaseDate <= $tomorrow) OR releaseDate >= ${
              today.plusDays(
                30,
              )
            }) AND (releaseDate = $today AND (recall = true OR indeterminateSentence = false))",
          )
        },
        isNull(),
      )
    }
  }
}
