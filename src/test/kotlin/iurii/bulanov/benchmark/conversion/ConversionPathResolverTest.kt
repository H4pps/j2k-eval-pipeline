package iurii.bulanov.benchmark.conversion

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BuildConfig
import iurii.bulanov.benchmark.config.CheckoutConfig
import iurii.bulanov.benchmark.config.JavaConfig
import iurii.bulanov.benchmark.config.RepositoryConfig
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversionPathResolverTest {
    @Test
    fun `resolves default conversion paths from benchmark id`() {
        val paths = ConversionPathResolver().resolve(testConfig("sample"))

        assertEquals(Path.of("build/j2k/sample/staging-source"), paths.stagingDirectory)
        assertEquals(Path.of("build/j2k/sample/generated-kotlin"), paths.generatedKotlinDirectory)
        assertEquals(Path.of("build/j2k/sample/conversion.json"), paths.conversionReport)
        assertEquals(Path.of("build/j2k/sample/logs"), paths.logsDirectory)
    }

    @Test
    fun `applies safe explicit output paths`() {
        val paths =
            ConversionPathResolver().resolve(
                testConfig("sample"),
                ConversionPathOverrides(
                    stagingDirectory = Path.of("build/custom/staging"),
                    generatedKotlinDirectory = Path.of("build/custom/generated"),
                    conversionReport = Path.of("build/custom/conversion.json"),
                    logsDirectory = Path.of("build/custom/logs"),
                ),
            )

        assertEquals(Path.of("build/custom/staging"), paths.stagingDirectory)
        assertEquals(Path.of("build/custom/generated"), paths.generatedKotlinDirectory)
        assertEquals(Path.of("build/custom/conversion.json"), paths.conversionReport)
        assertEquals(Path.of("build/custom/logs"), paths.logsDirectory)
    }

    @Test
    fun `rejects unsafe output paths`() {
        val exception =
            assertFailsWith<ConversionException> {
                ConversionPathResolver().resolve(
                    testConfig("sample"),
                    ConversionPathOverrides(
                        stagingDirectory = Path.of("../staging"),
                        generatedKotlinDirectory = null,
                        conversionReport = null,
                        logsDirectory = null,
                    ),
                )
            }

        assertContains(exception.message.orEmpty(), "stagingDir must not contain '..'")
    }

    /**
     * Builds a minimal benchmark config for path resolution tests.
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
}
