package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.stereotype.Component
import std.nooook.readinggardenkotlin.ReadingGardenKotlinApplication
import std.nooook.readinggardenkotlin.common.storage.StorageProperties
import std.nooook.readinggardenkotlin.modules.push.integration.FirebaseProperties

class ConfigurationPropertiesRegistrationTest {
    @Test
    fun `application should scan configuration properties for native compatibility`() {
        assertTrue(
            ReadingGardenKotlinApplication::class.java.isAnnotationPresent(ConfigurationPropertiesScan::class.java),
        )
    }

    @Test
    fun `storage properties should not be registered as component bean`() {
        assertFalse(StorageProperties::class.java.isAnnotationPresent(Component::class.java))
    }

    @Test
    fun `firebase properties should not be registered as component bean`() {
        assertFalse(FirebaseProperties::class.java.isAnnotationPresent(Component::class.java))
    }

    @Test
    fun `storage properties should expose java bean setter for native binding`() {
        assertTrue(
            StorageProperties::class.java.methods.any { method ->
                method.name == "setImagesRoot" && method.parameterCount == 1
            },
        )
    }

    @Test
    fun `firebase properties should expose java bean setters for native binding`() {
        assertTrue(
            FirebaseProperties::class.java.methods.any { method ->
                method.name == "setProjectId" && method.parameterCount == 1
            },
        )
        assertTrue(
            FirebaseProperties::class.java.methods.any { method ->
                method.name == "setServiceAccountFile" && method.parameterCount == 1
            },
        )
    }
}
