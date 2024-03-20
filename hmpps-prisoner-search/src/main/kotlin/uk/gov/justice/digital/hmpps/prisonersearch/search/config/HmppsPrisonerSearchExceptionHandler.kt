package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.BadRequestException
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.exceptions.NotFoundException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

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
  fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Method argument failure: ${e.message}",
          developerMessage = e.developerMessage(),
        ),
      ).also { log.info("MethodArgumentNotValid exception: {}", e.message) }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Method argument failure: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("MethodArgumentTypeMismatchException exception: {}", e.message) }

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

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleValidationException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Failed to parse request: ${e.message}",
          developerMessage = e.message,
        ),
      ).also { log.info("Failed to parse request: {}", e.message) }

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

  @ExceptionHandler(NotFoundException::class)
  fun handleEntityNotFoundException(e: NotFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.NOT_FOUND)
    .contentType(MediaType.APPLICATION_JSON)
    .body(ErrorResponse(status = HttpStatus.NOT_FOUND, developerMessage = e.message))

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleEntityNotFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.NOT_FOUND)
    .contentType(MediaType.APPLICATION_JSON)
    .body(ErrorResponse(status = HttpStatus.NOT_FOUND, developerMessage = e.message))

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
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.FORBIDDEN)
    .body(ErrorResponse(status = HttpStatus.FORBIDDEN))
    .also { log.debug("Forbidden (403) returned", e) }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

private fun MethodArgumentNotValidException.developerMessage(): String {
  return this.bindingResult.allErrors.joinToString { it.defaultMessage ?: "unknown" }
}
