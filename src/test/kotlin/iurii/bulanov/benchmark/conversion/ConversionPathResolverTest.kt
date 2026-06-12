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
    fun `resolves conversion paths under the requested kind`() {
        val paths = ConversionPathResolver().resolve(testConfig("sample"), ConverterKind.K1_OLD_DUMB)

        assertEquals(Path.of("build/j2k/sample/staging-source-abc123"), paths.stagingDirectory)
        assertEquals(Path.of("build/j2k/sample/k1-old-dumb/generated-kotlin"), paths.generatedKotlinDirectory)
        assertEquals(Path.of("build/j2k/sample/k1-old-dumb/conversion.json"), paths.conversionReport)
        assertEquals(Path.of("build/j2k/sample/k1-old-dumb/logs"), paths.logsDirectory)
    }

    @Test
    fun `puts each kind under its own subtree but shares staging`() {
        val paths = ConversionPathResolver().resolve(testConfig("sample"), ConverterKind.K2)

        assertEquals(Path.of("build/j2k/sample/staging-source-abc123"), paths.stagingDirectory)
        assertEquals(Path.of("build/j2k/sample/k2/generated-kotlin"), paths.generatedKotlinDirectory)
        assertEquals(Path.of("build/j2k/sample/k2/conversion.json"), paths.conversionReport)
        assertEquals(Path.of("build/j2k/sample/k2/logs"), paths.logsDirectory)
    }

    @Test
    fun `applies safe explicit output paths`() {
        val paths =
            ConversionPathResolver().resolve(
                testConfig("sample"),
                ConverterKind.K1_OLD_DUMB,
                overrides =
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
                    ConverterKind.K1_OLD_DUMB,
                    overrides =
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
