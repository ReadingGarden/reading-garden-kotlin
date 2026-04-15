package std.nooook.readinggardenkotlin

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import std.nooook.readinggardenkotlin.TestcontainersConfiguration

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ReadingGardenKotlinApplicationTests {
    @Test
    fun contextLoads() {
    }
}
