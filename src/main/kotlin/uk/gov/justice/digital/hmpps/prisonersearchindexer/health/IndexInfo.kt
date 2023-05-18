package uk.gov.justice.digital.hmpps.prisonersearchindexer.health

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonersearchindexer.services.IndexStatusService

@Component
class IndexInfo(
  private val indexStatusService: IndexStatusService,
) : InfoContributor {

  override fun contribute(builder: Info.Builder) {
    val indexStatus = indexStatusService.getCurrentIndex()
    builder.withDetail("index-status", indexStatus)
  }
}
