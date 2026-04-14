package std.nooook.readinggardenkotlin.common.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindingResult
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.ErrorResponse
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

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
    ): ResponseEntity<LegacyHttpResponse> =
        ResponseEntity
            .status(ex.statusCode)
            .body(LegacyResponses.error(ex.statusCode.value(), ex.reason ?: ex.message))

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
                message = if (ex.parameter.hasParameterAnnotation(RequestBody::class.java)) {
                    "Request body validation failed."
                } else {
                    "Request parameter validation failed."
                },
                errors = ex.bindingResult.toBindingErrorItems(
                    parameter = if (ex.parameter.hasParameterAnnotation(RequestBody::class.java)) {
                        null
                    } else {
                        ex.parameter.parameterName
                    },
                ),
            ),
            headers,
            status,
            request,
        )

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
        )

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
                    errors = ex.bindingResult.toBindingErrorItems(),
                ),
            )

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val detail = resolveDetail(ex, body, defaultMessage(status))
        val legacyBody = when {
            body is LegacyHttpResponse -> body
            ex is MissingServletRequestParameterException -> LegacyResponses.error(
                status = status.value(),
                message = detail,
                errors = listOf(
                    mapOf(
                        "parameter" to ex.parameterName,
                        "expectedType" to ex.parameterType,
                    ),
                ),
            )
            ex is HttpMessageNotReadableException -> LegacyResponses.error(
                status = status.value(),
                message = detail,
            )
            ex is TypeMismatchException -> LegacyResponses.error(
                status = status.value(),
                message = "Request parameter type mismatch.",
                errors = listOf(ex.toTypeMismatchErrorItem()),
            )
            ex is ServletRequestBindingException -> LegacyResponses.error(
                status = status.value(),
                message = detail,
                errors = ex.toRequestBindingErrorItems(),
            )
            body is ProblemDetail -> LegacyResponses.error(
                status = status.value(),
                message = detail,
            )
            else -> LegacyResponses.error(
                status = status.value(),
                message = detail,
            )
        }

        return super.handleExceptionInternal(ex, legacyBody, headers, status, request)!!
    }

    override fun createResponseEntity(
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val legacyBody = if (body is ProblemDetail) {
            LegacyResponses.error(status.value(), body.detail ?: defaultMessage(status))
        } else {
            body
        }
        return super.createResponseEntity(legacyBody, headers, status, request)
    }

    private fun BindingResult.toBindingErrorItems(parameter: String? = null): List<Map<String, Any?>> =
        fieldErrors.toFieldErrorItems(parameter) + globalErrors.toGlobalErrorItems(parameter)

    private fun List<FieldError>.toFieldErrorItems(parameter: String? = null): List<Map<String, Any?>> = map { fieldError ->
        linkedMapOf(
            "parameter" to parameter,
            "field" to fieldError.field,
            "message" to (fieldError.defaultMessage ?: "Invalid value"),
            "rejectedValue" to fieldError.rejectedValue,
        ).withoutNullValues()
    }

    private fun List<ObjectError>.toGlobalErrorItems(parameter: String? = null): List<Map<String, Any?>> = map { error ->
        linkedMapOf(
            "parameter" to parameter,
            "object" to error.objectName,
            "message" to (error.defaultMessage ?: "Invalid value"),
        ).withoutNullValues()
    }

    private fun List<ParameterValidationResult>.toParameterErrorItems(): List<Map<String, Any?>> =
        flatMap { result ->
            val parameterName = result.methodParameter.parameterName
            when (result) {
                is org.springframework.validation.method.ParameterErrors -> {
                    result.fieldErrors.toFieldErrorItems(parameterName) +
                        result.globalErrors.toGlobalErrorItems(parameterName)
                }
                else -> {
                    result.resolvableErrors.map { error ->
                        linkedMapOf(
                            "parameter" to parameterName,
                            "message" to error.defaultMessage,
                            "rejectedValue" to result.argument,
                            "containerIndex" to result.containerIndex,
                            "containerKey" to result.containerKey,
                        ).withoutNullValues()
                    }
                }
            }
        }

    private fun TypeMismatchException.toTypeMismatchErrorItem(): Map<String, Any?> =
        linkedMapOf(
            "parameter" to ((this as? MethodArgumentTypeMismatchException)?.name ?: propertyName),
            "message" to (message ?: "Type mismatch"),
            "rejectedValue" to value,
            "requiredType" to requiredType?.simpleName,
        ).withoutNullValues()

    private fun ServletRequestBindingException.toRequestBindingErrorItems(): List<Map<String, Any?>> =
        when (this) {
            is MissingRequestHeaderException -> listOf(
                linkedMapOf(
                    "source" to "header",
                    "parameter" to headerName,
                ).withoutNullValues(),
            )
            else -> emptyList()
        }

    private fun defaultMessage(status: HttpStatusCode): String =
        if (status.value() >= 500) ErrorCode.INTERNAL_SERVER_ERROR.detail else "Bad Request"

    private fun resolveDetail(
        ex: Exception,
        body: Any?,
        fallback: String,
    ): String = when {
        body is ProblemDetail && !body.detail.isNullOrBlank() -> body.detail!!
        ex is ErrorResponse && !ex.body.detail.isNullOrBlank() -> ex.body.detail!!
        else -> fallback
    }

    private fun Map<String, Any?>.withoutNullValues(): Map<String, Any?> =
        entries
            .filter { it.value != null }
            .associate { it.key to it.value }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
