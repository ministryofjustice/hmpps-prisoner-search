package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.AttributeNotFoundException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.NotFoundException

@RestControllerAdvice
class HmppsPrisonerSearchExceptionHandler {
  @ExceptionHandler(BadRequestException::class)
  fun handleBadRequestException(e: BadRequestException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("BadRequest exception: {}", e.message) }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
    log.debug("Bad request (400) returned", e)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST.value(),
          userMessage = "Method argument failure: ${e.message}",
          developerMessage = e.developerMessage(),
        ),
      ).also { log.info("MethodArgumentNotValid exception: {}", e.message) }
  }

  @ExceptionHandler(AttributeNotFoundException::class)
  fun handleAttributeNotFoundException(e: AttributeNotFoundException): ResponseEntity<ErrorResponse> {
    log.debug("Bad request (400) returned", e)
    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST.value(),
          userMessage = "Method argument failure: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("AttributeNotFoundException exception: {}", e.message) }
  }

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(ResponseStatusException::class)
  fun handleResponseStatusException(e: ResponseStatusException): ResponseEntity<ErrorResponse?>? =
    ResponseEntity
      .status(e.statusCode)
      .body(
        ErrorResponse(
          status = e.statusCode.value(),
          userMessage = "Response status error: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("Response status exception with message {}", e.message) }

  @ExceptionHandler(NotFoundException::class)
  fun handleEntityNotFoundException(e: NotFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.NOT_FOUND)
    .contentType(MediaType.APPLICATION_JSON)
    .body(ErrorResponse(status = HttpStatus.NOT_FOUND.value(), developerMessage = e.message))

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleEntityNotFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.NOT_FOUND)
    .contentType(MediaType.APPLICATION_JSON)
    .body(ErrorResponse(status = HttpStatus.NOT_FOUND.value(), developerMessage = e.message))

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse?>? =
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

private fun MethodArgumentNotValidException.developerMessage(): String {
  return this.bindingResult.allErrors.joinToString { it.defaultMessage ?: "unknown" }
}
