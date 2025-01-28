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
  ABSENT(false),
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

  @Field(type = FieldType.Keyword)
  @Schema(description = "The index currently active for searches", example = "GREEN")
  val currentIndex: SyncIndex,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The last time the current index started building", example = "2020-07-17T10:25:49.842Z")
  val currentIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The last time the current index finished building", example = "2020-07-17T11:35:29.833Z")
  val currentIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  @Schema(description = "The status of the current index before it became active", example = "COMPLETED")
  val currentIndexState: IndexState = IndexState.ABSENT,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The time the inactive index started building", example = "2020-07-17T12:26:48.822Z")
  val otherIndexStartBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
  @Schema(description = "The time the inactive index ended building", example = "null")
  val otherIndexEndBuildTime: LocalDateTime? = null,

  @Field(type = FieldType.Text)
  @Schema(description = "The status of the inactive index", example = "BUILDING")
  val otherIndexState: IndexState = IndexState.ABSENT,

) {

  val otherIndex
    @Schema(description = "The index currently available for rebuilding", example = "BLUE")
    get() = currentIndex.otherIndex()

  fun inProgress(): Boolean = this.otherIndexState == IndexState.BUILDING

  fun toBuildInProgress(): IndexStatus = this.copy(
    otherIndexStartBuildTime = LocalDateTime.now(),
    otherIndexEndBuildTime = null,
    otherIndexState = IndexState.BUILDING,
  )

  fun toBuildAbsent(): IndexStatus = this.copy(
    otherIndexStartBuildTime = null,
    otherIndexEndBuildTime = null,
    otherIndexState = IndexState.ABSENT,
  )

  fun toBuildComplete(): IndexStatus = this.copy(
    otherIndexEndBuildTime = LocalDateTime.now(),
    otherIndexState = IndexState.COMPLETED,
  )

  fun toSwitchIndex(): IndexStatus = this.copy(
    currentIndex = otherIndex,
    currentIndexStartBuildTime = otherIndexStartBuildTime,
    currentIndexEndBuildTime = otherIndexEndBuildTime,
    currentIndexState = otherIndexState,
    otherIndexStartBuildTime = currentIndexStartBuildTime,
    otherIndexEndBuildTime = currentIndexEndBuildTime,
    otherIndexState = currentIndexState,
  )

  fun toBuildCancelled(): IndexStatus = this.copy(otherIndexEndBuildTime = LocalDateTime.now(), otherIndexState = IndexState.CANCELLED)

  fun activeIndexes(): List<SyncIndex> = listOf(Pair(currentIndexState, currentIndex), Pair(otherIndexState, otherIndex))
    .filter { it.first.active }
    .map { it.second }

  fun activeIndexesEmpty(): Boolean = activeIndexes().isEmpty()

  @JsonIgnore
  fun isBuilding() = otherIndexState == IndexState.BUILDING

  @JsonIgnore
  fun isCancelled() = otherIndexState == IndexState.CANCELLED

  @JsonIgnore
  fun isAbsent() = otherIndexState == IndexState.ABSENT

  @JsonIgnore
  fun isNotBuilding() = isBuilding().not()

  companion object {
    fun newIndex() = IndexStatus(currentIndex = SyncIndex.NONE)
  }
}
