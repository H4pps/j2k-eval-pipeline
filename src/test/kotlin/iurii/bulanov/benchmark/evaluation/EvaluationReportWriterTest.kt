package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BuildConfig
import iurii.bulanov.benchmark.config.CheckoutConfig
import iurii.bulanov.benchmark.config.JavaConfig
import iurii.bulanov.benchmark.config.RepositoryConfig
import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.logging.StructuredLogger
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationReportWriterTest {
    @Test
    @Suppress("LongMethod")
    fun `writes assignment aligned json and markdown for partial conversions`() {
        val reportDirectory = Files.createTempDirectory("evaluation-report-")

        EvaluationReportWriter(logger = NoopLogger).write(sampleResult(reportDirectory))

        val jsonPath = reportDirectory.resolve("evaluation.json")
        val summaryPath = reportDirectory.resolve("summary.md")
        assertTrue(jsonPath.exists())
        assertTrue(summaryPath.exists())
        val json = jsonPath.readText()
        assertContains(json, "\"id\":\"sample\"")
        assertContains(json, "\"kind\":\"k1-old-dumb\"")
        assertContains(json, "\"java_file_count\":1")
        assertContains(json, "\"kotlin_file_count\":0")
        assertContains(json, "\"conversion\"")
        assertContains(json, "\"file_coverage\"")
        assertContains(json, "\"quality\"")
        assertContains(json, "\"content\"")
        assertContains(json, "\"nullability\"")
        assertContains(json, "\"control_flow_fidelity_score\":0.4")
        assertContains(json, "\"java_function_declaration_count\":1")
        assertContains(json, "\"content_shape_preserved_file_count\":1")
        assertContains(json, "\"return_preservation_ratio\":0.0")
        assertContains(json, "\"null_comparison_count\":1")
        assertContains(json, "\"nullability_cast_count\":1")
        assertContains(json, "\"safe_call_count\":0")
        assertContains(json, "\"nullability_inference_accuracy\":0.5")
        assertContains(json, "\"analysis\"")
        assertContains(json, "\"analysis_method\":\"structural_heuristics\"")
        assertContains(json, "\"missing_output_count\":1")
        assertContains(json, "\"content_warning_count\":1")
        assertContains(json, "\"nullability_warning_count\":1")
        assertContains(json, "\"quality_warning_count\":1")
        assertContains(json, "\"java_bean_accessors_missing_as_functions\"")
        assertContains(json, "\"java_bean_accessors_backed_by_kotlin_properties\"")

        val summary = summaryPath.readText()
        assertFalse(summary.contains("## Benchmark"))
        assertFalse(summary.contains("## Assignment Fit"))
        assertContains(summary, "## Result Interpretation")
        assertContains(summary, "## Conversion Execution")
        assertContains(summary, "## Kotlin Quality Warnings")
        assertContains(summary, "Static J2K produced a partial conversion")
        assertContains(summary, "Java files discovered: `1`")
        assertContains(summary, "Generated Kotlin is compared with the original Java source")
        assertContains(summary, "Java API names missing in Kotlin: `2`")
        assertContains(summary, "Kotlin-only API names: `1`")
        assertContains(summary, "`MissingClass`")
        assertContains(summary, "`newHelper`")
        assertContains(summary, "Java classes/records converted to Kotlin objects")
        assertContains(summary, "`Utility`")
        assertContains(summary, "full lists are in `evaluation.json` under `structure.name_diffs`")
        assertContains(summary, "Java bean accessors backed by Kotlin properties")
        assertContains(summary, "`getTitle`")
        assertContains(summary, "#### Classes and records")
        assertContains(summary, "Java classes/records missing in Kotlin")
        assertContains(summary, "#### Methods and functions")
        assertContains(summary, "Kotlin functions not present as Java methods")
        assertContains(summary, "## Content Preservation")
        assertContains(summary, "Missing Kotlin bodies: `1`")
        assertContains(summary, "Function declarations: Java `1`, Kotlin `1`")
        assertContains(summary, "Control-flow fidelity score: `0.400` (returns `0/1`, branches `0/0`, throws `0/0`, tries `0/0`)")
        assertContains(summary, "Content-shape preservation rate: `1.000` (`1/1` matched files preserved; mismatches `0`)")
        assertContains(
            summary,
            "Return density: Java `1.000`, Kotlin `0.000`, preservation `0.000` (returns per function `1/1` vs `0/1`)",
        )
        assertContains(summary, "`App.kt#run`")
        assertContains(summary, "## Nullability Signals")
        assertContains(summary, "Total nullability operations: `2` (null comparisons `1`, casts `1`, safe calls `0`)")
        assertContains(summary, "Nullability inference accuracy: `0.500`")
        assertContains(summary, "Nullable annotations not preserved: `1`")
        assertContains(summary, "`App.kt#name`")
        assertContains(summary, "Missing generated Kotlin files: `1`")
        assertContains(summary, "`App.kt`")
        assertContains(summary, "`generated_kotlin_directory_missing`")
    }

    @Test
    fun `writes assignment aligned markdown for complete conversions`() {
        val reportDirectory = Files.createTempDirectory("evaluation-report-complete-")

        EvaluationReportWriter(logger = NoopLogger).write(completeResult(reportDirectory))

        val summary = reportDirectory.resolve("summary.md").readText()
        assertContains(summary, "Static J2K generated Kotlin for every configured Java input")
        assertContains(summary, "Coverage: `1` of `1` configured Java inputs")
        assertContains(summary, "## Content Preservation")
        assertContains(summary, "## Nullability Signals")
        assertContains(summary, "## Notable Failures")
        assertContains(summary, "- None")
        assertFalse(summary.contains("Missing generated Kotlin files"))
    }

    /**
     * Builds a sample evaluation result for report rendering tests.
     */
    private fun sampleResult(reportDirectory: Path): EvaluationResult =
        EvaluationResult(
            config = testConfig("sample"),
            kind = ConverterKind.K1_OLD_DUMB,
            checkoutDirectory = Path.of("build/benchmarks/sample/source"),
            generatedKotlinDirectory = Path.of("build/j2k/sample/k1-old-dumb/generated-kotlin"),
            reportDirectory = reportDirectory,
            conversionReportPath = Path.of("build/j2k/sample/conversion.json"),
            checkoutReportPath = Path.of("build/benchmarks/sample/checkout.json"),
            javaFiles = sampleJavaFiles(),
            kotlinFiles = emptyList(),
            checkout = CheckoutEvaluation(available = true, buildStatus = "skipped", javaFileCount = 1, runBuild = false),
            conversion = sampleConversion(),
            fileCoverage = sampleFileCoverage(),
            structure = sampleStructure(),
            content = sampleContent(),
            nullability = sampleNullability(),
            quality = sampleQuality(),
            status = EvaluationStatus.COMPLETED_WITH_WARNINGS,
            warnings = sampleWarnings(),
        )

    /**
     * Builds a completed evaluation result for report rendering tests.
     */
    private fun completeResult(reportDirectory: Path): EvaluationResult =
        EvaluationResult(
            config = testConfig("complete"),
            kind = ConverterKind.K1_OLD_SMART,
            checkoutDirectory = Path.of("build/benchmarks/complete/source"),
            generatedKotlinDirectory = Path.of("build/j2k/complete/k1-old-smart/generated-kotlin"),
            reportDirectory = reportDirectory,
            conversionReportPath = Path.of("build/j2k/complete/conversion.json"),
            checkoutReportPath = Path.of("build/benchmarks/complete/checkout.json"),
            javaFiles = sampleJavaFiles(),
            kotlinFiles = sampleKotlinFiles(),
            checkout = CheckoutEvaluation(available = true, buildStatus = "skipped", javaFileCount = 1, runBuild = false),
            conversion =
                ConversionEvaluation(
                    available = true,
                    status = "completed",
                    sourceJavaFileCount = 1,
                    generatedKotlinFileCount = 1,
                    warningCount = 0,
                    errorCount = 0,
                    warnings = emptyList(),
                    errors = emptyList(),
                ),
            fileCoverage =
                FileCoverageMetrics(
                    javaFileCount = 1,
                    kotlinFileCount = 1,
                    matchedKotlinFileCount = 1,
                    missingKotlinFiles = emptyList(),
                    unexpectedKotlinFiles = emptyList(),
                    emptyGeneratedFiles = emptyList(),
                    packagePreservedCount = 1,
                    packageMismatchFiles = emptyList(),
                ),
            structure =
                StructuralMetrics(
                    javaTopLevelDeclarationCount = 1,
                    kotlinTopLevelDeclarationCount = 1,
                    javaClassLikeCount = 1,
                    kotlinClassLikeCount = 1,
                    javaInterfaceCount = 0,
                    kotlinInterfaceCount = 0,
                    javaEnumCount = 0,
                    kotlinEnumCount = 0,
                    javaMethodCount = 1,
                    kotlinFunctionCount = 1,
                    publicApiNameOverlapCount = 1,
                    missingPublicApiNames = emptyList(),
                    kotlinOnlyPublicApiNames = emptyList(),
                    nameDiffs = emptyNameDiffs(),
                ),
            content = emptyContent(),
            nullability = emptyNullability(),
            quality = emptyQuality(),
            status = EvaluationStatus.COMPLETED,
            warnings = emptyList(),
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
     * Builds a discovered Kotlin output fixture.
     */
    private fun sampleKotlinFiles(): List<DiscoveredSourceFile> =
        listOf(
            DiscoveredSourceFile(
                absolutePath = Path.of("build/j2k/sample/generated-kotlin/src/main/java/App.kt"),
                relativePath = Path.of("src/main/java/App.kt"),
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
            missingPublicApiNames = listOf("App", "MissingClass"),
            kotlinOnlyPublicApiNames = listOf("newHelper"),
            nameDiffs =
                StructuralNameDiffs(
                    classLike = StructuralNameDiff(missingInKotlin = listOf("MissingClass"), kotlinOnly = emptyList()),
                    interfaces = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
                    enums = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
                    objects = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
                    classLikeToObjectNames = listOf("Utility"),
                    javaBeanAccessorNames = listOf("getTitle"),
                    functions = StructuralNameDiff(missingInKotlin = listOf("App"), kotlinOnly = listOf("newHelper")),
                ),
        )

    /**
     * Builds empty grouped structural name diffs.
     */
    private fun emptyNameDiffs(): StructuralNameDiffs =
        StructuralNameDiffs(
            classLike = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
            interfaces = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
            enums = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
            objects = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
            classLikeToObjectNames = emptyList(),
            javaBeanAccessorNames = emptyList(),
            functions = StructuralNameDiff(missingInKotlin = emptyList(), kotlinOnly = emptyList()),
        )

    /**
     * Builds sample content metrics with one body preservation finding.
     */
    private fun sampleContent(): ContentMetrics =
        ContentMetrics(
            matchedFileCount = 1,
            javaNonEmptyMethodCount = 1,
            kotlinNonEmptyFunctionCount = 0,
            javaEmptyMethodCount = 0,
            kotlinEmptyFunctionCount = 1,
            missingKotlinBodies = listOf("App.kt#run"),
            contentShapeMismatchFiles = emptyList(),
            javaReturnCount = 1,
            kotlinReturnCount = 0,
            javaBranchCount = 0,
            kotlinBranchCount = 0,
            javaLoopCount = 0,
            kotlinLoopCount = 0,
            javaThrowCount = 0,
            kotlinThrowCount = 0,
            javaTryCount = 0,
            kotlinTryCount = 0,
            javaFunctionDeclarationCount = 1,
            kotlinFunctionDeclarationCount = 1,
            contentShapePreservedFileCount = 1,
            contentShapeMismatchFileCount = 0,
            returnPreservationRatio = 0.0,
            branchPreservationRatio = 1.0,
            throwPreservationRatio = 1.0,
            tryPreservationRatio = 1.0,
            controlFlowFidelityScore = 0.4,
            contentShapePreservationRate = 1.0,
            javaReturnDensity = 1.0,
            kotlinReturnDensity = 0.0,
            returnStatementDensityPreservation = 0.0,
            javaBranchComplexityIndex = 0.0,
            kotlinBranchComplexityIndex = 0.0,
            branchComplexityIndexPreservation = 1.0,
            findings =
                listOf(
                    EvaluationWarning(
                        code = "missing_kotlin_body",
                        message = "Generated Kotlin appears to be missing bodies for Java methods with executable bodies.",
                        path = "App.kt",
                        count = 1,
                    ),
                ),
        )

    /**
     * Builds sample nullability metrics with one annotation preservation finding.
     */
    private fun sampleNullability(): NullabilityMetrics =
        NullabilityMetrics(
            javaNullableAnnotationCount = 1,
            javaNotNullAnnotationCount = 0,
            kotlinNullableTypeCount = 0,
            contradictoryNullabilityPatterns = 1,
            nullComparisonCount = 1,
            nullabilityCastCount = 1,
            safeCallCount = 0,
            totalNullabilityOperationCount = 2,
            nullabilityInferenceAccuracy = 0.5,
            nullableAnnotationsNotPreserved = listOf("App.kt#name"),
            notNullAnnotationsBecameNullable = emptyList(),
            findings =
                listOf(
                    EvaluationWarning(
                        code = "nullable_annotation_not_preserved",
                        message = "Java nullable annotations were not reflected as nullable Kotlin declarations.",
                        path = "App.kt",
                        count = 1,
                    ),
                ),
        )

    /**
     * Builds empty content metrics.
     */
    private fun emptyContent(): ContentMetrics =
        ContentMetrics(
            matchedFileCount = 1,
            javaNonEmptyMethodCount = 1,
            kotlinNonEmptyFunctionCount = 1,
            javaEmptyMethodCount = 0,
            kotlinEmptyFunctionCount = 0,
            missingKotlinBodies = emptyList(),
            contentShapeMismatchFiles = emptyList(),
            javaReturnCount = 1,
            kotlinReturnCount = 1,
            javaBranchCount = 0,
            kotlinBranchCount = 0,
            javaLoopCount = 0,
            kotlinLoopCount = 0,
            javaThrowCount = 0,
            kotlinThrowCount = 0,
            javaTryCount = 0,
            kotlinTryCount = 0,
            javaFunctionDeclarationCount = 1,
            kotlinFunctionDeclarationCount = 1,
            contentShapePreservedFileCount = 1,
            contentShapeMismatchFileCount = 0,
            returnPreservationRatio = 1.0,
            branchPreservationRatio = 1.0,
            throwPreservationRatio = 1.0,
            tryPreservationRatio = 1.0,
            controlFlowFidelityScore = 1.0,
            contentShapePreservationRate = 1.0,
            javaReturnDensity = 1.0,
            kotlinReturnDensity = 1.0,
            returnStatementDensityPreservation = 1.0,
            javaBranchComplexityIndex = 0.0,
            kotlinBranchComplexityIndex = 0.0,
            branchComplexityIndexPreservation = 1.0,
            findings = emptyList(),
        )

    /**
     * Builds empty nullability metrics.
     */
    private fun emptyNullability(): NullabilityMetrics =
        NullabilityMetrics(
            javaNullableAnnotationCount = 0,
            javaNotNullAnnotationCount = 0,
            kotlinNullableTypeCount = 0,
            contradictoryNullabilityPatterns = 0,
            nullComparisonCount = 0,
            nullabilityCastCount = 0,
            safeCallCount = 0,
            totalNullabilityOperationCount = 0,
            nullabilityInferenceAccuracy = 1.0,
            nullableAnnotationsNotPreserved = emptyList(),
            notNullAnnotationsBecameNullable = emptyList(),
            findings = emptyList(),
        )

    /**
     * Builds sample quality metrics with one review finding.
     */
    private fun sampleQuality(): QualityMetrics =
        QualityMetrics(
            todoCount = 0,
            notNullAssertionCount = 1,
            notNullAssertionInCallCount = 0,
            anyNullableCount = 0,
            unresolvedImportCount = 0,
            javaInteropReferenceCount = 0,
            getterSetterCallCount = 0,
            nullableBooleanComparisonCount = 0,
            eagerPropertyInitializationCount = 0,
            findings =
                listOf(
                    EvaluationWarning(
                        code = "not_null_assertion",
                        message = "Generated Kotlin contains not-null assertions.",
                        path = "App.kt",
                        count = 1,
                    ),
                ),
        )

    /**
     * Builds empty quality metrics.
     */
    private fun emptyQuality(): QualityMetrics =
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
