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

        EvaluationReportWriter(logger = NoopLogger).write(sampleResult(reportDirectory))

        val jsonPath = reportDirectory.resolve("evaluation.json")
        val summaryPath = reportDirectory.resolve("summary.md")
        assertTrue(jsonPath.exists())
        assertTrue(summaryPath.exists())
        assertContains(jsonPath.readText(), "\"id\":\"sample\"")
        assertContains(jsonPath.readText(), "\"java_file_count\":1")
        assertContains(jsonPath.readText(), "\"kotlin_file_count\":0")
        assertContains(jsonPath.readText(), "\"conversion\"")
        assertContains(jsonPath.readText(), "\"file_coverage\"")
        assertContains(jsonPath.readText(), "\"quality\"")
        assertContains(summaryPath.readText(), "## Conversion Execution")
        assertContains(summaryPath.readText(), "## Kotlin Quality Warnings")
        assertContains(summaryPath.readText(), "Java files discovered: `1`")
        assertContains(summaryPath.readText(), "`generated_kotlin_directory_missing`")
    }

    /**
     * Builds a sample evaluation result for report rendering tests.
     */
    private fun sampleResult(reportDirectory: Path): EvaluationResult =
        EvaluationResult(
            config = testConfig("sample"),
            checkoutDirectory = Path.of("build/benchmarks/sample/source"),
            generatedKotlinDirectory = Path.of("build/j2k/sample/generated-kotlin"),
            reportDirectory = reportDirectory,
            conversionReportPath = Path.of("build/j2k/sample/conversion.json"),
            checkoutReportPath = Path.of("build/benchmarks/sample/checkout.json"),
            javaFiles = sampleJavaFiles(),
            kotlinFiles = emptyList(),
            checkout = CheckoutEvaluation(available = true, buildStatus = "skipped", javaFileCount = 1, runBuild = false),
            conversion = sampleConversion(),
            fileCoverage = sampleFileCoverage(),
            structure = sampleStructure(),
            quality = sampleQuality(),
            status = EvaluationStatus.COMPLETED_WITH_WARNINGS,
            warnings = sampleWarnings(),
        )

    /**
     * Builds a discovered Java input fixture.
     */
    private fun sampleJavaFiles(): List<DiscoveredSourceFile> =
        listOf(
            DiscoveredSourceFile(
                absolutePath = Path.of("build/benchmarks/sample/source/src/main/java/App.java"),
                relativePath = Path.of("src/main/java/App.java"),
            ),
        )

    /**
     * Builds sample conversion metadata.
     */
    private fun sampleConversion(): ConversionEvaluation =
        ConversionEvaluation(
            available = true,
            status = "completed_with_warnings",
            sourceJavaFileCount = 1,
            generatedKotlinFileCount = 0,
            warningCount = 1,
            errorCount = 0,
            warnings = listOf("sample conversion warning"),
            errors = emptyList(),
        )

    /**
     * Builds sample file coverage metrics.
     */
    private fun sampleFileCoverage(): FileCoverageMetrics =
        FileCoverageMetrics(
            javaFileCount = 1,
            kotlinFileCount = 0,
            matchedKotlinFileCount = 0,
            missingKotlinFiles = listOf("App.kt"),
            unexpectedKotlinFiles = emptyList(),
            emptyGeneratedFiles = emptyList(),
            packagePreservedCount = 0,
            packageMismatchFiles = emptyList(),
        )

    /**
     * Builds sample structural metrics.
     */
    private fun sampleStructure(): StructuralMetrics =
        StructuralMetrics(
            javaTopLevelDeclarationCount = 1,
            kotlinTopLevelDeclarationCount = 0,
            javaClassLikeCount = 1,
            kotlinClassLikeCount = 0,
            javaInterfaceCount = 0,
            kotlinInterfaceCount = 0,
            javaEnumCount = 0,
            kotlinEnumCount = 0,
            javaMethodCount = 1,
            kotlinFunctionCount = 0,
            publicApiNameOverlapCount = 0,
            missingPublicApiNames = listOf("App"),
        )

    /**
     * Builds empty sample quality metrics.
     */
    private fun sampleQuality(): QualityMetrics =
        QualityMetrics(
            todoCount = 0,
            notNullAssertionCount = 0,
            notNullAssertionInCallCount = 0,
            anyNullableCount = 0,
            unresolvedImportCount = 0,
            javaInteropReferenceCount = 0,
            getterSetterCallCount = 0,
            nullableBooleanComparisonCount = 0,
            eagerPropertyInitializationCount = 0,
            findings = emptyList(),
        )

    /**
     * Builds sample evaluator warnings.
     */
    private fun sampleWarnings(): List<EvaluationWarning> =
        listOf(
            EvaluationWarning(
                code = "generated_kotlin_directory_missing",
                message = "Generated Kotlin directory does not exist yet.",
                path = "build/j2k/sample/generated-kotlin",
            ),
        )

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
