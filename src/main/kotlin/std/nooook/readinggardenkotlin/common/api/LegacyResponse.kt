package std.nooook.readinggardenkotlin.common.api

data class LegacyHttpResponse(
    val resp_code: Int,
    val resp_msg: String,
    val errors: List<Map<String, Any?>>? = null,
)

data class LegacyDataResponse<T>(
    val resp_code: Int,
    val resp_msg: String,
    val data: T,
)
