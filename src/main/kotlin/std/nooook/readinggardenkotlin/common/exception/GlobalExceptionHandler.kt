package std.nooook.readinggardenkotlin.common.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import std.nooook.readinggardenkotlin.common.api.LegacyHttpResponse
import std.nooook.readinggardenkotlin.common.api.LegacyResponses

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity
            .status(ex.errorCode.status)
            .body(LegacyResponses.error(ex.errorCode.status.value(), ex.message ?: ex.errorCode.detail))

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        @Suppress("UNUSED_PARAMETER") ex: ConstraintViolationException,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity
            .status(ErrorCode.VALIDATION_ERROR.status)
            .body(LegacyResponses.error(ErrorCode.VALIDATION_ERROR.status.value(), "Request parameter validation failed."))

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(
        ex: Exception,
    ): ResponseEntity<LegacyHttpResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(LegacyResponses.error(500, ex.message ?: ErrorCode.INTERNAL_SERVER_ERROR.detail))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        handleExceptionInternal(
            ex,
            LegacyResponses.error(status.value(), "Request body validation failed."),
            headers,
            status,
            request,
        )!!

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        handleExceptionInternal(
            ex,
            LegacyResponses.error(status.value(), "Request parameter validation failed."),
            headers,
            status,
            request,
        )!!

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
