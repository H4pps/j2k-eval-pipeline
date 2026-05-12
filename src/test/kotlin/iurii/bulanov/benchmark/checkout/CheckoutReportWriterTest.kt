package iurii.bulanov.benchmark.checkout

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BuildConfig
import iurii.bulanov.benchmark.config.CheckoutConfig
import iurii.bulanov.benchmark.config.JavaConfig
import iurii.bulanov.benchmark.config.RepositoryConfig
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckoutReportWriterTest {
    @Test
    fun `renders deterministic checkout json`() {
        val result = CheckoutResult(config = testConfig("sample"), javaFileCount = 2, buildStatus = BuildStatus.PASSED)

        val json = CheckoutReportWriter(logger = NoopLogger).renderJson(result, runBuild = true)

        assertEquals(
            """
            {"benchmark":{"id":"sample", "name":"Sample", "role":"primary", "repository_source":"https://example.com/source", "repository_upstream":"https://example.com/upstream", "repository_ref":"abc123"}, "checkout_directory":"build/benchmarks/sample/source", "java_file_count":2, "build_status":"passed", "run_build":true}

            """.trimIndent(),
            json,
        )
    }

    @Test
    fun `writes checkout json to requested report path`() {
        val result = CheckoutResult(config = testConfig("sample"), javaFileCount = 1, buildStatus = BuildStatus.SKIPPED)
        val reportPath = Files.createTempDirectory("checkout-report-").resolve("nested/checkout.json")

        val writtenPath = CheckoutReportWriter(logger = NoopLogger).write(result, runBuild = false, reportPath = reportPath)

        assertEquals(reportPath.normalize(), writtenPath)
        assertTrue(reportPath.exists())
        assertEquals(
            CheckoutReportWriter(logger = NoopLogger).renderJson(result, runBuild = false),
            reportPath.readText(),
        )
    }

    @Test
    fun `resolves default checkout report path from benchmark id`() {
        val path = CheckoutReportWriter(logger = NoopLogger).resolveReportPath(testConfig("sample"), overridePath = null)

        assertEquals(Path.of("build/benchmarks/sample/checkout.json"), path)
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
