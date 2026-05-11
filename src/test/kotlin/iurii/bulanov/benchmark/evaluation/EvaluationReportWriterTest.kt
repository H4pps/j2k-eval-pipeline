package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BuildConfig
import iurii.bulanov.benchmark.config.CheckoutConfig
import iurii.bulanov.benchmark.config.JavaConfig
import iurii.bulanov.benchmark.config.RepositoryConfig
import iurii.bulanov.logging.StructuredLogger
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class EvaluationReportWriterTest {
    @Test
    fun `writes deterministic json and markdown reports`() {
        val reportDirectory = Files.createTempDirectory("evaluation-report-")
        val result =
            EvaluationResult(
                config = testConfig("sample"),
                checkoutDirectory = Path.of("build/benchmarks/sample/source"),
                generatedKotlinDirectory = Path.of("build/j2k/sample/generated-kotlin"),
                reportDirectory = reportDirectory,
                javaFiles =
                    listOf(
                        DiscoveredSourceFile(
                            absolutePath = Path.of("build/benchmarks/sample/source/src/main/java/App.java"),
                            relativePath = Path.of("src/main/java/App.java"),
                        ),
                    ),
                kotlinFiles = emptyList(),
                status = EvaluationStatus.COMPLETED_WITH_WARNINGS,
                warnings =
                    listOf(
                        EvaluationWarning(
                            code = "generated_kotlin_directory_missing",
                            message = "Generated Kotlin directory does not exist yet.",
                            path = "build/j2k/sample/generated-kotlin",
                        ),
                    ),
            )

        EvaluationReportWriter(logger = NoopLogger).write(result)

        val jsonPath = reportDirectory.resolve("evaluation.json")
        val summaryPath = reportDirectory.resolve("summary.md")
        assertTrue(jsonPath.exists())
        assertTrue(summaryPath.exists())
        assertContains(jsonPath.readText(), "\"id\":\"sample\"")
        assertContains(jsonPath.readText(), "\"java_file_count\":1")
        assertContains(jsonPath.readText(), "\"kotlin_file_count\":0")
        assertContains(summaryPath.readText(), "Java files discovered: `1`")
        assertContains(summaryPath.readText(), "`generated_kotlin_directory_missing`")
    }

    /**
     * Builds a minimal benchmark config for report rendering tests.
     */
    private fun testConfig(id: String): BenchmarkConfig =
        BenchmarkConfig(
            id = id,
            name = "Sample",
            role = "primary",
            description = "sample",
            repository =
                RepositoryConfig(
                    upstream = "https://example.com/upstream",
                    source = "https://example.com/source",
                    ref = "abc123",
                    branch = "main",
                ),
            checkout = CheckoutConfig(directory = "build/benchmarks/$id/source"),
            java = JavaConfig(sourceRoots = listOf("src/main/java")),
            build = BuildConfig(tool = "maven", workingDirectory = ".", commands = listOf("mvn test")),
        )

    private object NoopLogger : StructuredLogger {
        override fun info(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit

        override fun error(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit
    }
}
