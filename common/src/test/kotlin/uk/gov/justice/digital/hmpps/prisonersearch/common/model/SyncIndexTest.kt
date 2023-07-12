package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SyncIndexTest {

  @Test
  fun `Other index`() {
    assertThat(SyncIndex.GREEN.otherIndex()).isEqualTo(SyncIndex.BLUE)
    assertThat(SyncIndex.BLUE.otherIndex()).isEqualTo(SyncIndex.GREEN)
    assertThat(SyncIndex.GREEN.otherIndex().otherIndex()).isEqualTo(SyncIndex.GREEN)
  }
}
