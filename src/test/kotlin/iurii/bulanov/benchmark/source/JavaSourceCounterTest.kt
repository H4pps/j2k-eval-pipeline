package iurii.bulanov.benchmark.source

import iurii.bulanov.benchmark.checkout.CheckoutException
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaSourceCounterTest {
    @Test
    fun `counts java files across configured roots`() {
        val checkout = Files.createTempDirectory("checkout-")
        checkout.toFile().deleteOnExit()

        val rootA = checkout.resolve("src/main/java")
        val rootB = checkout.resolve("src/test/java")
        rootA.createDirectories()
        rootB.createDirectories()
        rootA.resolve("App.java").writeText("class App {}")
        rootA.resolve("Nested.java").writeText("class Nested {}")
        rootB.resolve("AppTest.java").writeText("class AppTest {}")

        val count =
            JavaSourceCounter().countJavaFiles(
                checkoutDirectory = checkout,
                sourceRoots = listOf("src/main/java", "src/test/java")
            )

        assertEquals(3, count)
    }

    @Test
    fun `missing source root fails`() {
        val checkout = Files.createTempDirectory("checkout-")
        checkout.toFile().deleteOnExit()
        checkout.resolve("src/main/java").createDirectories()

        val exception =
            assertFailsWith<CheckoutException> {
                JavaSourceCounter().countJavaFiles(
                    checkoutDirectory = checkout,
                    sourceRoots = listOf("src/main/java", "src/other/java")
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
            assertFailsWith<CheckoutException> {
                JavaSourceCounter().countJavaFiles(
                    checkoutDirectory = checkout,
                    sourceRoots = listOf("src/main/java")
                )
            }

        assertContains(exception.message.orEmpty(), "no Java files found under configured source roots")
    }
}
