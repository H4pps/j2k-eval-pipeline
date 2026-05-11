package iurii.bulanov.source

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceFileDiscoveryTest {
    @Test
    fun `discovers java files across configured roots`() {
        val checkout = Files.createTempDirectory("checkout-")
        checkout.toFile().deleteOnExit()

        val rootA = checkout.resolve("src/main/java")
        val rootB = checkout.resolve("src/test/java")
        rootA.resolve("nested").createDirectories()
        rootB.createDirectories()
        rootA.resolve("App.java").writeText("class App {}")
        rootA.resolve("nested/Nested.java").writeText("class Nested {}")
        rootB.resolve("AppTest.java").writeText("class AppTest {}")
        rootB.resolve("notes.txt").writeText("not source")

        val result =
            SourceFileDiscovery().discoverJavaFiles(
                checkoutDirectory = checkout,
                sourceRoots = listOf("src/main/java", "src/test/java"),
            )

        assertTrue(result.directoryExists)
        assertEquals(3, result.files.size)
        assertEquals(
            listOf("src/main/java/App.java", "src/main/java/nested/Nested.java", "src/test/java/AppTest.java"),
            result.files.map { it.relativePath.toString() },
        )
    }

    @Test
    fun `missing source root fails`() {
        val checkout = Files.createTempDirectory("checkout-")
        checkout.toFile().deleteOnExit()
        checkout.resolve("src/main/java").createDirectories()

        val exception =
            assertFailsWith<SourceDiscoveryException> {
                SourceFileDiscovery().discoverJavaFiles(
                    checkoutDirectory = checkout,
                    sourceRoots = listOf("src/main/java", "src/other/java"),
                )
            }

        assertContains(exception.message.orEmpty(), "missing Java source roots: src/other/java")
    }

    @Test
    fun `zero java files fails`() {
        val checkout = Files.createTempDirectory("checkout-")
        checkout.toFile().deleteOnExit()
        checkout.resolve("src/main/java").createDirectories()

        val exception =
            assertFailsWith<SourceDiscoveryException> {
                SourceFileDiscovery().discoverJavaFiles(
                    checkoutDirectory = checkout,
                    sourceRoots = listOf("src/main/java"),
                )
            }

        assertContains(exception.message.orEmpty(), "no Java files found under configured source roots")
    }

    @Test
    fun `discovers kotlin files under generated output directory`() {
        val generated = Files.createTempDirectory("generated-kotlin-")
        generated.toFile().deleteOnExit()
        generated.resolve("com/example").createDirectories()
        generated.resolve("com/example/App.kt").writeText("class App")
        generated.resolve("com/example/Other.kt").writeText("class Other")
        generated.resolve("com/example/notes.java").writeText("class Notes")

        val result = SourceFileDiscovery().discoverKotlinFiles(generated)

        assertTrue(result.directoryExists)
        assertEquals(2, result.files.size)
        assertEquals(listOf("com/example/App.kt", "com/example/Other.kt"), result.files.map { it.relativePath.toString() })
    }

    @Test
    fun `missing generated kotlin directory returns empty discovery result`() {
        val generated = Files.createTempDirectory("generated-parent-").resolve("missing")

        val result = SourceFileDiscovery().discoverKotlinFiles(generated)

        assertFalse(result.directoryExists)
        assertEquals(emptyList(), result.files)
    }
}
