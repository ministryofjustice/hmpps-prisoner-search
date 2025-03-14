package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

const val INDEX_STATUS_ID = "STATUS"

enum class IndexState(val active: Boolean) {
  BUILDING(true),
  CANCELLED(false),
  COMPLETED(true),
}

@Document(indexName = "prisoner-index-status")
@Schema(description = "The status of the two indexes, the current index being actively used for searches and the other index being inactive but available for rebuilding")
data class IndexStatus(
  @Id
  @Field(type = FieldType.Keyword)
  @JsonIgnore
  val id: String = INDEX_STATUS_ID,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The last time the current index started building", example = "2020-07-17T10:25:49.842Z")
  val currentIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The last time the current index finished building", example = "2020-07-17T11:35:29.833Z")
  val currentIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  @Schema(description = "The status of the current index before it became active", example = "COMPLETED")
  val currentIndexState: IndexState = IndexState.COMPLETED,
) {
  fun inProgress(): Boolean = this.currentIndexState == IndexState.BUILDING

  fun toBuildInProgress(): IndexStatus = this.copy(
    currentIndexStartBuildTime = LocalDateTime.now(),
    currentIndexEndBuildTime = null,
    currentIndexState = IndexState.BUILDING,
  )

  fun toBuildComplete(): IndexStatus = this.copy(
    currentIndexEndBuildTime = LocalDateTime.now(),
    currentIndexState = IndexState.COMPLETED,
  )

  fun toBuildCancelled(): IndexStatus = this.copy(currentIndexEndBuildTime = LocalDateTime.now(), currentIndexState = IndexState.CANCELLED)

  @JsonIgnore
  fun isBuilding() = currentIndexState == IndexState.BUILDING

  @JsonIgnore
  fun isCancelled() = currentIndexState == IndexState.CANCELLED

  @JsonIgnore
  fun isNotBuilding() = isBuilding().not()

//  companion object {
//    // fun newIndex() = IndexStatus(currentIndex = SyncIndex.NONE)
//  }
}
