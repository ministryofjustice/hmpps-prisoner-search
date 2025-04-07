package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.Diffable
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentIncentive(
  @Schema(description = "Incentive level. Will never be null.")
  val level: IncentiveLevel? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(required = true, description = "Date time of the incentive. Will never be null.", example = "2022-11-10T15:47:24")
  val dateTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(required = true, description = "Schedule new review date", example = "2022-11-10")
  val nextReviewDate: LocalDate? = null,
) : Diffable<CurrentIncentive> {
  override fun diff(other: CurrentIncentive): DiffResult<CurrentIncentive> = getDiffResult(this, other)
}

data class IncentiveLevel(
  @Schema(description = "code. Will never be null.", example = "STD")
  val code: String? = null,

  @Schema(description = "description. Will never be null.", example = "Standard")
  val description: String? = null,
)
