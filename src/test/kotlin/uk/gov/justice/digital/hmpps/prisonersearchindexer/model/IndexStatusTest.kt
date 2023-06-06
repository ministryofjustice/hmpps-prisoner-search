package uk.gov.justice.digital.hmpps.prisonersearchindexer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.ABSENT
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.BUILDING
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.CANCELLED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.IndexState.COMPLETED
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.BLUE
import uk.gov.justice.digital.hmpps.prisonersearchindexer.model.SyncIndex.GREEN
import java.time.LocalDateTime

internal class IndexStatusTest {

  @Test
  fun `will switch indexes`() {
    val oldStartTime = LocalDateTime.now().minusHours(24)
    val oldEndTime = oldStartTime.plusMinutes(10)
    val newStartTime = LocalDateTime.now().minusMinutes(5)
    val indexStatus = IndexStatus(currentIndex = GREEN, currentIndexState = COMPLETED, currentIndexEndBuildTime = oldEndTime, currentIndexStartBuildTime = oldStartTime, otherIndexState = BUILDING, otherIndexStartBuildTime = newStartTime, otherIndexEndBuildTime = null)

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
    assertThat(IndexStatus(currentIndex = BLUE, currentIndexState = ABSENT, otherIndexState = ABSENT).activeIndexes()).containsExactlyInAnyOrder()
    assertThat(IndexStatus(currentIndex = BLUE, currentIndexState = BUILDING, otherIndexState = ABSENT).activeIndexes()).containsExactlyInAnyOrder(BLUE)
    assertThat(IndexStatus(currentIndex = BLUE, currentIndexState = CANCELLED, otherIndexState = BUILDING).activeIndexes()).containsExactlyInAnyOrder(GREEN)
    assertThat(IndexStatus(currentIndex = BLUE, currentIndexState = COMPLETED, otherIndexState = BUILDING).activeIndexes()).containsExactlyInAnyOrder(BLUE, GREEN)
  }
}
