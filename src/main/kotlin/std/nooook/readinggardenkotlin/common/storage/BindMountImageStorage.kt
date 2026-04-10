package std.nooook.readinggardenkotlin.common.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class BindMountImageStorage(
    @Value("\${app.storage.images-root:/opt/reading-garden/data/images}")
    private val imagesRoot: String,
) : ImageStorage {
    private val rootPath: Path by lazy {
        Path.of(imagesRoot).toAbsolutePath().normalize()
    }

    private val trashRootPath: Path by lazy {
        val rootName = rootPath.fileName?.toString() ?: "images"
        rootPath.parent.resolve("${rootName}-trash").toAbsolutePath().normalize()
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

    override fun stageDelete(relativePath: String): ImageStorage.StagedDelete {
        val targetPath = resolveTargetPath(relativePath)
        val stagedPath = resolveStagedPath(relativePath)
        Files.createDirectories(stagedPath.parent)
        try {
            move(targetPath, stagedPath)
        } catch (_: NoSuchFileException) {
            return NoOpStagedDelete
        }

        return PendingDelete(targetPath = targetPath, stagedPath = stagedPath)
    }

    private fun resolveTargetPath(relativePath: String): Path {
        val targetPath = rootPath.resolve(relativePath).normalize()
        require(targetPath.startsWith(rootPath)) { "Invalid image path: $relativePath" }
        return targetPath
    }

    private fun resolveStagedPath(relativePath: String): Path {
        val stagedPath = trashRootPath
            .resolve(UUID.randomUUID().toString().replace("-", ""))
            .resolve(relativePath)
            .normalize()
        require(stagedPath.startsWith(trashRootPath)) { "Invalid staged image path: $relativePath" }
        return stagedPath
    }

    private fun move(
        source: Path,
        target: Path,
    ) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cleanupEmptyParents(start: Path) {
        var current = start.parent
        while (current != null && current.startsWith(trashRootPath)) {
            try {
                Files.delete(current)
            } catch (_: DirectoryNotEmptyException) {
                return
            } catch (_: NoSuchFileException) {
                return
            }
            current = current.parent
        }
    }

    private object NoOpStagedDelete : ImageStorage.StagedDelete {
        override fun commit() = Unit

        override fun rollback() = Unit
    }

    private inner class PendingDelete(
        private val targetPath: Path,
        private val stagedPath: Path,
    ) : ImageStorage.StagedDelete {
        override fun commit() {
            Files.deleteIfExists(stagedPath)
            cleanupEmptyParents(stagedPath)
        }

        override fun rollback() {
            Files.createDirectories(targetPath.parent)
            try {
                move(stagedPath, targetPath)
            } catch (_: NoSuchFileException) {
                return
            }
            cleanupEmptyParents(stagedPath)
        }
    }
}
