package iurii.bulanov.benchmark.config

import iurii.bulanov.benchmark.checkout.CheckoutException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BenchmarkConfigParserTest {
    @Test
    fun `parses hikaricp config`() {
        val config = BenchmarkConfigParser().parse(Path.of("benchmarks/hikaricp.yml"))

        assertEquals("hikaricp", config.id)
        assertEquals("HikariCP", config.name)
        assertEquals("primary", config.role)
        assertEquals("https://github.com/H4pps/HikariCP", config.repository.source)
        assertEquals("build/benchmarks/hikaricp/source", config.checkout.directory)
        assertEquals(listOf("src/main/java"), config.java.sourceRoots)
        assertEquals(listOf("mvn -q -DskipTests package"), config.build.commands)
    }

    @Test
    fun `parses spring petclinic config`() {
        val config = BenchmarkConfigParser().parse(Path.of("benchmarks/spring-petclinic.yml"))

        assertEquals("spring-petclinic", config.id)
        assertEquals("Spring PetClinic", config.name)
        assertEquals("calibration", config.role)
        assertEquals("https://github.com/H4pps/spring-petclinic", config.repository.source)
        assertEquals("build/benchmarks/spring-petclinic/source", config.checkout.directory)
        assertEquals(listOf("src/main/java"), config.java.sourceRoots)
        assertEquals(listOf("./mvnw -q -DskipTests package"), config.build.commands)
    }

    @Test
    fun `missing required field fails clearly`() {
        val configPath =
            writeTempConfig(
                """
                name: Missing ID
                role: primary
                description: test
                repository:
                  upstream: https://example.com/upstream
                  source: https://example.com/source
                  ref: abcdef
                  branch: main
                checkout:
                  directory: build/benchmarks/missing-id/source
                java:
                  sourceRoots:
                    - src/main/java
                build:
                  tool: maven
                  workingDirectory: .
                  commands:
                    - mvn -q -DskipTests package
                """.trimIndent(),
            )

        val exception =
            assertFailsWith<CheckoutException> {
                BenchmarkConfigParser().parse(configPath)
            }

        assertContains(exception.message.orEmpty(), "missing required config key: id")
    }

    @Test
    fun `empty java source roots fails`() {
        val configPath =
            writeTempConfig(
                """
                id: test
                name: Test
                role: primary
                description: test
                repository:
                  upstream: https://example.com/upstream
                  source: https://example.com/source
                  ref: abcdef
                  branch: main
                checkout:
                  directory: build/benchmarks/test/source
                java:
                  sourceRoots: []
                build:
                  tool: maven
                  workingDirectory: .
                  commands:
                    - mvn -q -DskipTests package
                """.trimIndent(),
            )

        val exception =
            assertFailsWith<CheckoutException> {
                BenchmarkConfigParser().parse(configPath)
            }

        assertContains(exception.message.orEmpty(), "java.sourceRoots must be a non-empty list")
    }

    @Test
    fun `empty build commands fails`() {
        val configPath =
            writeTempConfig(
                """
                id: test
                name: Test
                role: primary
                description: test
                repository:
                  upstream: https://example.com/upstream
                  source: https://example.com/source
                  ref: abcdef
                  branch: main
                checkout:
                  directory: build/benchmarks/test/source
                java:
                  sourceRoots:
                    - src/main/java
                build:
                  tool: maven
                  workingDirectory: .
                  commands: []
                """.trimIndent(),
            )

        val exception =
            assertFailsWith<CheckoutException> {
                BenchmarkConfigParser().parse(configPath)
            }

        assertContains(exception.message.orEmpty(), "build.commands must be a non-empty list")
    }

    @Test
    fun `unsafe checkout directories fail clearly`() {
        val unsafeDirectories =
            listOf(
                "/tmp/hikaricp/source" to "checkout.directory must be a relative path",
                "build/benchmarks/../hikaricp/source" to "checkout.directory must not contain '..'",
                "tmp/benchmarks/hikaricp/source" to "checkout.directory must start with build/benchmarks/",
            )

        unsafeDirectories.forEach { (checkoutDirectory, expectedMessage) ->
            val configPath = writeTempConfig(validConfig(checkoutDirectory))

            val exception =
                assertFailsWith<CheckoutException> {
                    BenchmarkConfigParser().parse(configPath)
                }

            assertContains(exception.message.orEmpty(), expectedMessage)
        }
    }

    /**
     * Writes a temporary benchmark config file for parser validation tests.
     */
    private fun writeTempConfig(content: String): Path {
        val file = Files.createTempFile("benchmark-config-", ".yml")
        file.writeText(content)
        file.toFile().deleteOnExit()
        return file
    }

    /**
     * Builds a complete valid config while allowing checkout directory variants.
     */
    private fun validConfig(checkoutDirectory: String): String =
        """
        id: test
        name: Test
        role: primary
        description: test
        repository:
          upstream: https://example.com/upstream
          source: https://example.com/source
          ref: abcdef
          branch: main
        checkout:
          directory: $checkoutDirectory
        java:
          sourceRoots:
            - src/main/java
        build:
          tool: maven
          workingDirectory: .
          commands:
            - mvn -q -DskipTests package
        """.trimIndent()
}
