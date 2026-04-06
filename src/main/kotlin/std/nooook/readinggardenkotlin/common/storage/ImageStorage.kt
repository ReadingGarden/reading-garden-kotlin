package std.nooook.readinggardenkotlin.common.storage

interface ImageStorage {
    interface StagedDelete {
        fun commit()

        fun rollback()
    }

    fun save(
        relativeDirectory: String,
        originalFilename: String?,
        content: ByteArray,
    ): String

    fun delete(relativePath: String)

    fun stageDelete(relativePath: String): StagedDelete
}
