package std.nooook.readinggardenkotlin.common.storage

interface ImageStorage {
    fun save(
        relativeDirectory: String,
        originalFilename: String?,
        content: ByteArray,
    ): String

    fun delete(relativePath: String)
}
