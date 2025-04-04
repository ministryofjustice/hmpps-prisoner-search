package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PrisonerTest {
  @Test
  fun `all Prisoner fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", Prisoner::class.java)
    }
  }

  @Test
  fun `all PrisonerAlias fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", PrisonerAlias::class.java)
    }
  }

  @Test
  fun `all PrisonerAlert fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", PrisonerAlert::class.java)
    }
  }

  @Test
  fun `all CurrentIncentive fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", CurrentIncentive::class.java)
    }
  }

  @Test
  fun `all BodyPartDetail fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", BodyPartDetail::class.java)
    }
  }

  @Test
  fun `all Address fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", Address::class.java)
    }
  }

  @Test
  fun `all EmailAddress fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", EmailAddress::class.java)
    }
  }

  @Test
  fun `all PhoneNumber fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", PhoneNumber::class.java)
    }
  }

  @Test
  fun `all Identifier fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", Identifier::class.java)
    }
  }

  @Test
  fun `all Offence fields are nullable`() {
    assertDoesNotThrow {
      jacksonObjectMapper().readValue("{}", Offence::class.java)
    }
  }
}