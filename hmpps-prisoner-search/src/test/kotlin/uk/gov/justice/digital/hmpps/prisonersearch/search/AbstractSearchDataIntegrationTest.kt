package uk.gov.justice.digital.hmpps.prisonersearch.search

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner

/**
 * Test class to initialise the standard set of search data only once.
 * Subclasses will get the same set of search data and we have implemented our own custom junit orderer to ensure that
 * no other type of tests run inbetween and cause a re-index to ruin our data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSearchDataIntegrationTest : AbstractSearchIntegrationTest() {
  private companion object {
    private var initialiseSearchData = true
  }

  @BeforeEach
  override fun setup() {
    if (initialiseSearchData) {
      super.setup()
      initialiseSearchData = false
    }
  }

  /**
   * final here to ensure that different data isn't loaded by subclasses.
   * If you need different data then extend AbstractSearchIntegrationTest instead
   */
  final override fun loadPrisonerData() {
    val prisoners = listOf(
      "A1090AA", "A7089EZ", "A7089FB", "A7089FX", "A7090AB", "A7090AD", "A7090AF", "A7090BB",
      "A7090BD", "A7090BF", "A9999AB", "A9999RA", "A9999RC", "A7089EY", "A7089FA", "A7089FC", "A7090AA", "A7090AC",
      "A7090AE", "A7090BA", "A7090BC", "A7090BE", "A9999AA", "A9999AC", "A9999RB",
    )
    prisoners.forEach {
      val sourceAsString = "/prisoners/prisoner$it.json".readResourceAsText()
      val prisoner = gson.fromJson(sourceAsString, Prisoner::class.java)
      prisonerRepository.save(prisoner)
    }
    waitForPrisonerLoading(prisoners.size)
  }
}
