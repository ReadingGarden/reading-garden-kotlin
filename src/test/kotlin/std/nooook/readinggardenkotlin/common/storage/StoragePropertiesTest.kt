package std.nooook.readinggardenkotlin.common.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class StoragePropertiesTest {
    @Test
    fun `default images root should match legacy server path`() {
        assertEquals("/opt/reading-garden/data/images", StorageProperties().imagesRoot)
    }
}
