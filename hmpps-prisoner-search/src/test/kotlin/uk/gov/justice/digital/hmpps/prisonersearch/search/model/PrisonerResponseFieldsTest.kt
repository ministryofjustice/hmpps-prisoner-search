package uk.gov.justice.digital.hmpps.prisonersearch.search.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.findComplexObjectTypes
import kotlin.reflect.KClass

@JsonTest
class PrisonerResponseFieldsTest(@Autowired private val objectMapper: ObjectMapper) {

  companion object {
    @JvmStatic
    fun prisonerTypes() = findComplexObjectTypes(Prisoner::class)
  }

  @ParameterizedTest
  @MethodSource("prisonerTypes")
  fun `all types in the Prisoner class have nullable fields`(type: KClass<*>) {
    assertDoesNotThrow {
      objectMapper.readValue("{}", type.java)
    }
  }
}
