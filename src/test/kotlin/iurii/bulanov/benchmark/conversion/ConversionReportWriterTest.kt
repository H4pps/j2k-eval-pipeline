package iurii.bulanov.benchmark.conversion

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BuildConfig
import iurii.bulanov.benchmark.config.CheckoutConfig
import iurii.bulanov.benchmark.config.JavaConfig
import iurii.bulanov.benchmark.config.RepositoryConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains

class ConversionReportWriterTest {
    @Test
    fun `renders deterministic conversion json`() {
        val report =
            ConversionReport(
                config = testConfig(),
                status = ConversionStatus.PARTIAL,
                sourceJavaFileCount = 2,
                generatedKotlinFileCount = 1,
                paths =
                    ConversionPaths(
                        stagingDirectory = Path.of("build/j2k/sample/staging-source"),
                        generatedKotlinDirectory = Path.of("build/j2k/sample/generated-kotlin"),
                        conversionReport = Files.createTempDirectory("conversion-report-").resolve("conversion.json"),
                        logsDirectory = Path.of("build/j2k/sample/logs"),
                    ),
                converterCommand = listOf("j2k-convert", "--config", "benchmarks/sample.yml"),
                warnings = listOf("sample warning"),
                errors = listOf("src/main/java/Sample.java: converter failed"),
            )

        val json = ConversionReportWriter().renderJson(report)

        assertContains(json, "\"id\":\"sample\"")
        assertContains(json, "\"status\":\"partial\"")
        assertContains(json, "\"source_java_file_count\":2")
        assertContains(json, "\"generated_kotlin_file_count\":1")
        assertContains(json, "\"warnings\":[\"sample warning\"]")
        assertContains(json, "\"errors\":[\"src/main/java/Sample.java: converter failed\"]")
    }

    /**
     * Builds a minimal benchmark config for report rendering tests.
     */
    private fun testConfig(): BenchmarkConfig =
        BenchmarkConfig(
            id = "sample",
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
            checkout = CheckoutConfig(directory = "build/benchmarks/sample/source"),
            java = JavaConfig(sourceRoots = listOf("src/main/java")),
            build = BuildConfig(tool = "maven", workingDirectory = ".", commands = listOf("mvn test")),
        )
}
