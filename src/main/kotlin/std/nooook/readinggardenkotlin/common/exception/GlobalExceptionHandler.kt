package std.nooook.readinggardenkotlin.common.exception

import jakarta.validation.ConstraintViolationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
        request: HttpServletRequest,
    ): ProblemDetail = buildProblem(
        errorCode = ex.errorCode,
        detail = ex.message ?: ex.errorCode.detail,
        instance = URI.create(request.requestURI),
    )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ProblemDetail = buildProblem(
        errorCode = ErrorCode.VALIDATION_ERROR,
        detail = "Request parameter validation failed.",
        instance = URI.create(request.requestURI),
    ).apply {
        setProperty(
            "errors",
            ex.constraintViolations.map { violation ->
                mapOf(
                    "parameter" to violation.propertyPath.toString().substringAfterLast("."),
                    "message" to violation.message,
                )
            },
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(
        ex: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.error("Unhandled exception", ex)
        return buildProblem(
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
            instance = URI.create(request.requestURI),
        )
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problem = buildProblem(
            errorCode = ErrorCode.VALIDATION_ERROR,
            detail = "Request body validation failed.",
            instance = request.instanceUri(),
        ).apply {
            setProperty("errors", ex.bindingResult.fieldErrors.toErrorItems())
        }

        return handleExceptionInternal(ex, problem, headers, status, request)!!
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problem = buildProblem(
            errorCode = ErrorCode.VALIDATION_ERROR,
            detail = "Request parameter validation failed.",
            instance = request.instanceUri(),
        ).apply {
            setProperty("errors", ex.parameterValidationResults.toParameterErrorItems())
        }

        return handleExceptionInternal(ex, problem, headers, status, request)!!
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(
        ex: BindException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val problem = buildProblem(
            errorCode = ErrorCode.VALIDATION_ERROR,
            detail = "Request binding failed.",
            instance = URI.create(request.requestURI),
        ).apply {
            setProperty("errors", ex.bindingResult.fieldErrors.toErrorItems())
        }

        return problem
    }

    private fun buildProblem(
        errorCode: ErrorCode,
        detail: String = errorCode.detail,
        instance: URI? = null,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(errorCode.status, detail).apply {
        title = errorCode.title
        this.instance = instance
        setProperty("code", errorCode.code)
    }

    private fun List<FieldError>.toErrorItems(): List<Map<String, Any?>> = map { fieldError ->
        mapOf(
            "field" to fieldError.field,
            "message" to (fieldError.defaultMessage ?: "Invalid value"),
            "rejectedValue" to fieldError.rejectedValue,
        )
    }

    private fun List<ParameterValidationResult>.toParameterErrorItems(): List<Map<String, String?>> =
        flatMap { result ->
            result.resolvableErrors.map { error ->
                mapOf(
                    "parameter" to result.methodParameter.parameterName,
                    "message" to error.defaultMessage,
                )
            }
        }

    private fun WebRequest.instanceUri(): URI? =
        (this as? ServletWebRequest)?.request?.requestURI?.let(URI::create)

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
