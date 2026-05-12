package iurii.bulanov.benchmark.conversion

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceTreeStagerTest {
    @Test
    fun `stages source tree while excluding generated and vcs directories`() {
        val source = Files.createTempDirectory("source-staging-input-")
        val staging = Files.createTempDirectory("source-staging-output-")
        source.resolve("src/main/java/com/example").createDirectories()
        source.resolve("src/main/java/com/example/App.java").writeText("class App {}")
        source.resolve(".git").createDirectories()
        source.resolve(".git/config").writeText("[core]")
        source.resolve("build/classes").createDirectories()
        source.resolve("build/classes/App.class").writeText("compiled")

        val result = SourceTreeStager().stage(source, staging)

        assertEquals(1, result.copiedFileCount)
        assertTrue(staging.resolve("src/main/java/com/example/App.java").exists())
        assertFalse(staging.resolve(".git/config").exists())
        assertFalse(staging.resolve("build/classes/App.class").exists())
    }
}
