package uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions

class BadRequestException(msg: String) : RuntimeException(msg)

class NotFoundException(msg: String) : RuntimeException(msg)

class AttributeNotFoundException(msg: String) : RuntimeException(msg)
