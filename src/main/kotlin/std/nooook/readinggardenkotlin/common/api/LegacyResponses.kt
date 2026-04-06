package std.nooook.readinggardenkotlin.common.api

object LegacyResponses {
    fun ok(message: String) = LegacyHttpResponse(resp_code = 200, resp_msg = message)

    fun created(message: String, data: Any) =
        LegacyDataResponse(resp_code = 201, resp_msg = message, data = data)

    fun error(
        status: Int,
        message: String,
        errors: List<Map<String, Any?>>? = null,
    ) = LegacyHttpResponse(resp_code = status, resp_msg = message, errors = errors)
}
