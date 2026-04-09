package std.nooook.readinggardenkotlin.common.config

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import std.nooook.readinggardenkotlin.ReadingGardenKotlinApplication

class ConfigurationPropertiesRegistrationTest {
    @Test
    fun `application should not scan configuration properties for native compatibility workaround`() {
        assertFalse(
            ReadingGardenKotlinApplication::class.java.declaredAnnotations.any {
                it.annotationClass.qualifiedName == "org.springframework.boot.context.properties.ConfigurationPropertiesScan"
            },
        )
    }

    @Test
    fun `storage properties class should be removed`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("std.nooook.readinggardenkotlin.common.storage.StorageProperties")
        }
    }

    @Test
    fun `firebase properties class should be removed`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("std.nooook.readinggardenkotlin.modules.push.integration.FirebaseProperties")
        }
    }

    @Test
    fun `web mvc config should not declare multipart config bean for native compatibility`() {
        assertFalse(
            WebMvcConfig::class.java.declaredMethods.any { it.name == "multipartConfigElement" },
        )
    }
}
