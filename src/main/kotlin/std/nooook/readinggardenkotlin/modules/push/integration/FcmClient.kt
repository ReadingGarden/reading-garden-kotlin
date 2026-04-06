package std.nooook.readinggardenkotlin.modules.push.integration

interface FcmClient {
    fun sendToMany(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
    ): List<Map<String, Any>>
}
