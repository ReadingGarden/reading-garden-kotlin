package std.nooook.readinggardenkotlin.common.exception

class ApiException(
    val errorCode: ErrorCode,
    message: String? = null,
) : RuntimeException(message ?: errorCode.detail)
