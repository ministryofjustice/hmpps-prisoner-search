package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.Diffable
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

open class Incentive : Diffable<Incentive> {
  @Id
  @Field(type = FieldType.Keyword)
  @Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  var prisonerNumber: String? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Incentive level")
  @DiffableProperty(DiffCategory.INCENTIVE_LEVEL)
  var currentIncentive: CurrentIncentive? = null

  override fun diff(other: Incentive): DiffResult<Incentive> = getDiffResult(this, other)
}

@Document(indexName = "incentive-search-a")
class IncentiveA : Incentive()

@Document(indexName = "incentive-search-b")
class IncentiveB : Incentive()
