package std.nooook.readinggardenkotlin.common.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class BindMountImageStorage(
    private val storageProperties: StorageProperties,
) : ImageStorage {
    private val rootPath: Path by lazy {
        Path.of(storageProperties.imagesRoot).toAbsolutePath().normalize()
    }

    override fun save(
        relativeDirectory: String,
        originalFilename: String?,
        content: ByteArray,
    ): String {
        val safeOriginalFilename = originalFilename?.let { Path.of(it).fileName.toString() }.orEmpty()
        val extension = safeOriginalFilename.substringAfterLast('.', "")
            .takeIf { safeOriginalFilename.contains('.') && it.isNotBlank() }
            ?.let { ".$it" }
            .orEmpty()
        val storedFilename = UUID.randomUUID().toString().replace("-", "") + extension
        val relativePath = Path.of(relativeDirectory, storedFilename).toString().replace('\\', '/')
        val targetPath = resolveTargetPath(relativePath)

        Files.createDirectories(targetPath.parent)
        Files.write(targetPath, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

        return relativePath
    }

    override fun delete(relativePath: String) {
        val targetPath = resolveTargetPath(relativePath)
        Files.delete(targetPath)
    }

    private fun resolveTargetPath(relativePath: String): Path {
        val targetPath = rootPath.resolve(relativePath).normalize()
        require(targetPath.startsWith(rootPath)) { "Invalid image path: $relativePath" }
        return targetPath
    }
}
