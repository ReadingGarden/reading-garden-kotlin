package std.nooook.readinggardenkotlin.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val title: String,
    val detail: String,
) {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Bad request", "The request could not be processed."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", "Request validation failed."),
    RESOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "RESOURCE_NOT_FOUND",
        "Resource not found",
        "The requested resource does not exist.",
    ),
    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_SERVER_ERROR",
        "Internal server error",
        "An unexpected error occurred.",
    ),
}
