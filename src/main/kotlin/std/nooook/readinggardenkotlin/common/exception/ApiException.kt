package std.nooook.readinggardenkotlin.common.exception

class ApiException(
    val errorCode: ErrorCode,
    detail: String? = null,
) : RuntimeException(detail ?: errorCode.detail)
