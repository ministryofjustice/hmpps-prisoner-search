package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SyncIndexTest {

  @Test
  fun `Other index`() {
    assertThat(SyncIndex.GREEN.otherIndex(INDEX_STATUS_ID)).isEqualTo(SyncIndex.BLUE)
    assertThat(SyncIndex.BLUE.otherIndex(INDEX_STATUS_ID)).isEqualTo(SyncIndex.GREEN)
    assertThat(SyncIndex.GREEN.otherIndex(INDEX_STATUS_ID).otherIndex(INDEX_STATUS_ID)).isEqualTo(SyncIndex.GREEN)
  }
}
