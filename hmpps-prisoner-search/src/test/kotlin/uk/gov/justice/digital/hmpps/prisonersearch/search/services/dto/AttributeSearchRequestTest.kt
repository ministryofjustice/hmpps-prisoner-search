package uk.gov.justice.digital.hmpps.prisonersearch.search.services.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import java.time.LocalDate

@JsonTest
class AttributeSearchRequestTest(@Autowired val objectMapper: ObjectMapper) {

  @Test
  fun `firstName is John`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
            "matchers": [
              {
                "joinType": "AND",
                "textMatchers": [
                  {
                    "attribute": "firstName",
                    "condition": "IS",
                    "searchTerm": "John"
                  }
                ]
              }
            ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND lastName is Smith`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
            "matchers": [
              {
                "joinType": "AND",
                "textMatchers": [
                  {
                    "attribute": "firstName",
                    "condition": "IS",
                    "searchTerm": "John"
                  },
                  {
                    "attribute": "lastName",
                    "condition": "IS",
                    "searchTerm": "Smith"
                  }
                ]
              }
            ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
              TextMatcher(
                attribute = "lastName",
                condition = TextCondition.IS,
                searchTerm = "Smith",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND (lastName is Smith OR lastName is Jones)`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
            "matchers": [
              {
                "joinType": "AND",
                "textMatchers": [
                  {
                    "attribute": "firstName",
                    "condition": "IS",
                    "searchTerm": "John"
                  }
                ],
                "children" : [
                  {
                    "joinType": "OR",
                    "textMatchers": [
                      {
                        "attribute": "lastName",
                        "condition": "IS",
                        "searchTerm": "Smith"
                      },
                      {
                        "attribute": "lastName",
                        "condition": "IS",
                        "searchTerm": "Jones"
                      }
                    ]
                  }
                ]
              }
            ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            children = listOf(
              Matcher(
                joinType = JoinType.OR,
                textMatchers = listOf(
                  TextMatcher(
                    attribute = "lastName",
                    condition = TextCondition.IS,
                    searchTerm = "Smith",
                  ),
                  TextMatcher(
                    attribute = "lastName",
                    condition = TextCondition.IS,
                    searchTerm = "Jones",
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `(firstName is John AND lastName is Smith) OR (firstName is Jack AND lastName is not Jones)`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
                {
                  "joinType": "OR",
                  "children": [
                    {
                      "joinType": "AND",
                      "textMatchers": [
                        {
                          "attribute": "firstName",
                          "condition": "IS",
                          "searchTerm": "John"
                        },
                        {
                          "attribute": "lastName",
                          "condition": "IS",
                          "searchTerm": "Smith"
                        }
                      ]
                    },
                    {
                      "joinType": "AND",
                      "textMatchers": [
                        {
                          "attribute": "firstName",
                          "condition": "IS",
                          "searchTerm": "Jack"
                        },
                        {
                          "attribute": "lastName",
                          "condition": "IS_NOT",
                          "searchTerm": "Jones"
                        }
                      ]
                    }
                  ]
                }
              ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.OR,
            children = listOf(
              Matcher(
                joinType = JoinType.AND,
                textMatchers = listOf(
                  TextMatcher(
                    attribute = "firstName",
                    condition = TextCondition.IS,
                    searchTerm = "John",
                  ),
                  TextMatcher(
                    attribute = "lastName",
                    condition = TextCondition.IS,
                    searchTerm = "Smith",
                  ),
                ),
              ),
              Matcher(
                joinType = JoinType.AND,
                textMatchers = listOf(
                  TextMatcher(
                    attribute = "firstName",
                    condition = TextCondition.IS,
                    searchTerm = "Jack",
                  ),
                  TextMatcher(
                    attribute = "lastName",
                    condition = TextCondition.IS_NOT,
                    searchTerm = "Jones",
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND recall is true`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "booleanMatchers": [
                   {
                     "attribute": "recall",
                     "condition": true
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            booleanMatchers = listOf(
              BooleanMatcher(
                attribute = "recall",
                condition = true,
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND heightCentimetres GTE 150`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "integerMatchers": [
                   {
                     "attribute": "heightCentimetres",
                     "minValue": 150 
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            integerMatchers = listOf(
              IntegerMatcher(
                attribute = "heightCentimetres",
                minValue = 150,
                minInclusive = true,
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND shoeSize between 11 and 12`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "integerMatchers": [
                   {
                     "attribute": "shoeSize",
                     "minValue": 11,
                     "maxValue": 12
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            integerMatchers = listOf(
              IntegerMatcher(
                attribute = "shoeSize",
                minValue = 11,
                minInclusive = true,
                maxValue = 12,
                maxInclusive = true,
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND receptionDate after 2023-01-01 AND releaseDate before 2024-01-01`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "dateMatchers": [
                   {
                     "attribute": "receptionDate",
                     "minValue": "2023-01-01"
                   },
                    {
                      "attribute": "releaseDate",
                      "maxValue": "2024-01-01",
                      "maxInclusive": false
                    }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            dateMatchers = listOf(
              DateMatcher(
                attribute = "receptionDate",
                minValue = LocalDate.parse("2023-01-01"),
              ),
              DateMatcher(
                attribute = "releaseDate",
                maxValue = LocalDate.parse("2024-01-01"),
                maxInclusive = false,
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND receptionDate is 2023-01-01`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "dateMatchers": [
                   {
                     "attribute": "receptionDate",
                     "minValue": "2023-01-01",
                     "maxValue": "2023-01-01"
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            dateMatchers = listOf(
              DateMatcher(
                attribute = "receptionDate",
                minValue = LocalDate.parse("2023-01-01"),
                minInclusive = true,
                maxValue = LocalDate.parse("2023-01-01"),
                maxInclusive = true,
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND tattoo on shoulder contains dragon`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   },
                   {
                     "attribute": "tattoos.bodyPart",
                     "condition": "IS",
                     "searchTerm": "shoulder"
                   },
                   {
                     "attribute": "tattoos.comment",
                     "condition": "CONTAINS",
                     "searchTerm": "dragon"
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
              TextMatcher(
                attribute = "tattoos.bodyPart",
                condition = TextCondition.IS,
                searchTerm = "shoulder",
              ),
              TextMatcher(
                attribute = "tattoos.comment",
                condition = TextCondition.CONTAINS,
                searchTerm = "dragon",
              ),
            ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `firstName is John AND (scar on face OR scar on head)`() {
    val request = objectMapper.readValue<AttributeSearchRequest>(
      """{
             "matchers": [
               {
                 "joinType": "AND",
                 "textMatchers": [
                   {
                     "attribute": "firstName",
                     "condition": "IS",
                     "searchTerm": "John"
                   }
                 ],
                 "children" : [
                   {
                     "joinType": "OR",
                     "textMatchers": [
                       {
                         "attribute": "scars.bodyPart",
                         "condition": "IS",
                         "searchTerm": "face"
                       },
                       {
                         "attribute": "scars.bodyPart",
                         "condition": "IS",
                         "searchTerm": "head"
                       }
                     ]
                   }
                 ]
               }
             ]
          }
      """.trimIndent(),
    )

    assertThat(request).isEqualTo(
      AttributeSearchRequest(
        matchers = listOf(
          Matcher(
            joinType = JoinType.AND,
            textMatchers = listOf(
              TextMatcher(
                attribute = "firstName",
                condition = TextCondition.IS,
                searchTerm = "John",
              ),
            ),
            children = listOf(
              Matcher(
                joinType = JoinType.OR,
                textMatchers = listOf(
                  TextMatcher(
                    attribute = "scars.bodyPart",
                    condition = TextCondition.IS,
                    searchTerm = "face",
                  ),
                  TextMatcher(
                    attribute = "scars.bodyPart",
                    condition = TextCondition.IS,
                    searchTerm = "head",
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    )
  }
}
