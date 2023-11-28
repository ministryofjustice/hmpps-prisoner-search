package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.IndexConflictException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.PrisonerNotFoundException
import uk.gov.justice.digital.hmpps.prisonersearch.indexer.services.WrongIndexRequestedException

@RestControllerAdvice
class HmppsPrisonerSearchIndexerExceptionHandler {
  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: Exception): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(ErrorResponse(status = BAD_REQUEST, developerMessage = e.message))
    .also {
      log.debug("Bad request (400) returned {}", e.message)
    }

  @ExceptionHandler(ResponseStatusException::class)
  fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(e.statusCode)
      .body(
        ErrorResponse(
          status = e.statusCode.value(),
          userMessage = "Response status error: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("Response status exception with message {}", e.message) }

  @ExceptionHandler(IndexConflictException::class)
  fun handleIndexConflictException(e: IndexConflictException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(CONFLICT)
      .body(
        ErrorResponse(
          status = CONFLICT,
          userMessage = "Index conflict exception: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.error("Index conflict exception", e) }

  @ExceptionHandler(PrisonerNotFoundException::class)
  fun handlePrisonerNotFoundException(e: PrisonerNotFoundException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(NOT_FOUND)
      .body(
        ErrorResponse(
          status = NOT_FOUND,
          userMessage = "Prisoner not found exception: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.error("Prisoner not found exception", e) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleEntityNotFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .contentType(MediaType.APPLICATION_JSON)
    .body(ErrorResponse(status = NOT_FOUND.value(), developerMessage = e.message))

  @ExceptionHandler(WrongIndexRequestedException::class)
  fun handleWrongIndexRequestedException(e: WrongIndexRequestedException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Wrong index requested exception: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.error("Wrong index requested exception", e) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(INTERNAL_SERVER_ERROR)
      .body(
        ErrorResponse(
          status = INTERNAL_SERVER_ERROR,
          userMessage = "Unexpected error: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.error("Unexpected exception", e) }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleException(e: AccessDeniedException?): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.FORBIDDEN)
    .body(ErrorResponse(status = HttpStatus.FORBIDDEN.value()))
    .also { log.debug("Forbidden (403) returned", e) }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class ErrorResponse(
  val status: Int,
  val errorCode: Int? = null,
  val userMessage: String? = null,
  val developerMessage: String? = null,
  val moreInfo: String? = null,
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
    moreInfo: String? = null,
  ) :
    this(status.value(), errorCode, userMessage, developerMessage, moreInfo)
}
