package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex.GREEN
import java.time.LocalDateTime

internal class IndexStatusTest {

  @Test
  fun `will switch indexes`() {
    val oldStartTime = LocalDateTime.now().minusHours(24)
    val oldEndTime = oldStartTime.plusMinutes(10)
    val newStartTime = LocalDateTime.now().minusMinutes(5)
    val indexStatus = IndexStatus(
      id = INDEX_STATUS_ID,
      currentIndex = GREEN,
      currentIndexState = COMPLETED,
      currentIndexEndBuildTime = oldEndTime,
      currentIndexStartBuildTime = oldStartTime,
      otherIndexState = BUILDING,
      otherIndexStartBuildTime = newStartTime,
      otherIndexEndBuildTime = null,
    )

    val newStatus = indexStatus.toSwitchIndex()

    assertThat(newStatus.currentIndex).isEqualTo(BLUE)
    assertThat(newStatus.currentIndexStartBuildTime).isEqualTo(newStartTime)
    assertThat(newStatus.otherIndex).isEqualTo(GREEN)
    assertThat(newStatus.otherIndexEndBuildTime).isEqualTo(oldEndTime)
    assertThat(newStatus.otherIndexStartBuildTime).isEqualTo(oldStartTime)
    assertThat(newStatus.otherIndexState).isEqualTo(COMPLETED)
  }

  @Test
  fun `Will find active indexes`() {
    assertThat(IndexStatus(id = INDEX_STATUS_ID, currentIndex = BLUE, currentIndexState = ABSENT, otherIndexState = ABSENT).activeIndexes()).containsExactlyInAnyOrder()
    assertThat(IndexStatus(id = INDEX_STATUS_ID, currentIndex = BLUE, currentIndexState = BUILDING, otherIndexState = ABSENT).activeIndexes()).containsExactlyInAnyOrder(BLUE)
    assertThat(IndexStatus(id = INDEX_STATUS_ID, currentIndex = BLUE, currentIndexState = CANCELLED, otherIndexState = BUILDING).activeIndexes()).containsExactlyInAnyOrder(GREEN)
    assertThat(IndexStatus(id = INDEX_STATUS_ID, currentIndex = BLUE, currentIndexState = COMPLETED, otherIndexState = BUILDING).activeIndexes()).containsExactlyInAnyOrder(BLUE, GREEN)
  }
}
