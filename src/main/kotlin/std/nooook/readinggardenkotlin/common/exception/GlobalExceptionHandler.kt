package std.nooook.readinggardenkotlin.common.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterValidationResult
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
        ex: ConstraintViolationException,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity
            .status(ErrorCode.VALIDATION_ERROR.status)
            .body(
                LegacyResponses.error(
                    status = ErrorCode.VALIDATION_ERROR.status.value(),
                    message = "Request parameter validation failed.",
                    errors = ex.constraintViolations.map { violation ->
                        mapOf(
                            "parameter" to violation.propertyPath.toString().substringAfterLast("."),
                            "message" to violation.message,
                        )
                    },
                ),
            )

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(
        ex: Exception,
    ): ResponseEntity<LegacyHttpResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(LegacyResponses.error(500, ErrorCode.INTERNAL_SERVER_ERROR.detail))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        handleExceptionInternal(
            ex,
            LegacyResponses.error(
                status = status.value(),
                message = "Request body validation failed.",
                errors = ex.bindingResult.fieldErrors.toFieldErrorItems(),
            ),
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
            LegacyResponses.error(
                status = status.value(),
                message = "Request parameter validation failed.",
                errors = ex.parameterValidationResults.toParameterErrorItems(),
            ),
            headers,
            status,
            request,
        )!!

    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                LegacyResponses.error(
                    status = HttpStatus.BAD_REQUEST.value(),
                    message = "Request binding failed.",
                    errors = ex.bindingResult.fieldErrors.toFieldErrorItems(),
                ),
            )

    private fun List<FieldError>.toFieldErrorItems(): List<Map<String, Any?>> = map { fieldError ->
        mapOf(
            "field" to fieldError.field,
            "message" to (fieldError.defaultMessage ?: "Invalid value"),
            "rejectedValue" to fieldError.rejectedValue,
        )
    }

    private fun List<ParameterValidationResult>.toParameterErrorItems(): List<Map<String, Any?>> =
        flatMap { result ->
            result.resolvableErrors.map { error ->
                mapOf(
                    "parameter" to result.methodParameter.parameterName,
                    "message" to error.defaultMessage,
                )
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
