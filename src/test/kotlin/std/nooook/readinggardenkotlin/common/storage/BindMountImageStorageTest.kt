package std.nooook.readinggardenkotlin.common.storage

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindMountImageStorageTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `stage delete should permanently remove file on commit`() {
        val storage = storage()
        val targetPath = writeImage("book/commit.png", "commit-bytes".toByteArray())

        val stagedDelete = storage.stageDelete("book/commit.png")

        assertFalse(Files.exists(targetPath))

        stagedDelete.commit()

        assertFalse(Files.exists(targetPath))
        assertTrue(trashFiles().isEmpty())
    }

    @Test
    fun `stage delete should restore file on rollback`() {
        val storage = storage()
        val expectedBytes = "rollback-bytes".toByteArray()
        val targetPath = writeImage("memo/rollback.png", expectedBytes)

        val stagedDelete = storage.stageDelete("memo/rollback.png")

        assertFalse(Files.exists(targetPath))

        stagedDelete.rollback()

        assertTrue(Files.exists(targetPath))
        assertContentEquals(expectedBytes, Files.readAllBytes(targetPath))
        assertTrue(trashFiles().isEmpty())
    }

    @Test
    fun `stage delete should ignore missing file`() {
        val storage = storage()

        val stagedDelete = storage.stageDelete("book/missing.png")

        stagedDelete.commit()
        stagedDelete.rollback()

        assertTrue(trashFiles().isEmpty())
    }

    @Test
    fun `stage delete rollback should ignore missing staged file`() {
        val storage = storage()
        val targetPath = writeImage("book/rollback-missing.png", "rollback-missing".toByteArray())
        val stagedDelete = storage.stageDelete("book/rollback-missing.png")

        assertFalse(Files.exists(targetPath))
        trashFiles().forEach(Files::deleteIfExists)

        stagedDelete.rollback()

        assertFalse(Files.exists(targetPath))
        assertTrue(trashFiles().isEmpty())
    }

    private fun storage(): BindMountImageStorage {
        return BindMountImageStorage(tempDir.resolve("images").toString())
    }

    private fun writeImage(
        relativePath: String,
        content: ByteArray,
    ): Path {
        val targetPath = tempDir.resolve("images").resolve(relativePath)
        Files.createDirectories(targetPath.parent)
        Files.write(targetPath, content)
        return targetPath
    }

    private fun trashFiles(): List<Path> {
        val trashRoot = tempDir.resolve("images-trash")
        if (!Files.exists(trashRoot)) {
            return emptyList()
        }
        return Files.walk(trashRoot).use { paths ->
            paths.filter(Files::isRegularFile).toList()
        }
    }
}
