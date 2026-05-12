package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path

/**
 * Parser-independent request for running the evaluator skeleton.
 */
data class EvaluationRequest(
    val configPath: Path,
    val generatedKotlinDirectory: Path?,
    val reportDirectory: Path?,
    val conversionReport: Path? = null,
    val checkoutReport: Path? = null,
    val githubSummaryPath: Path? = null,
)

/**
 * High-level status for an evaluator run that completed infrastructure work.
 */
enum class EvaluationStatus {
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
}

/**
 * Human-reviewable warning emitted by evaluator checks.
 */
data class EvaluationWarning(
    val code: String,
    val message: String,
    val path: String? = null,
    val count: Int? = null,
)

/**
 * Phase 4 evaluation result used by JSON and Markdown reports.
 */
data class EvaluationResult(
    val config: BenchmarkConfig,
    val checkoutDirectory: Path,
    val generatedKotlinDirectory: Path,
    val reportDirectory: Path,
    val conversionReportPath: Path,
    val checkoutReportPath: Path,
    val javaFiles: List<DiscoveredSourceFile>,
    val kotlinFiles: List<DiscoveredSourceFile>,
    val checkout: CheckoutEvaluation,
    val conversion: ConversionEvaluation,
    val fileCoverage: FileCoverageMetrics,
    val structure: StructuralMetrics,
    val quality: QualityMetrics,
    val status: EvaluationStatus,
    val warnings: List<EvaluationWarning>,
)

/**
 * Original benchmark checkout/build metadata available to the evaluator.
 */
data class CheckoutEvaluation(
    val available: Boolean,
    val buildStatus: String,
    val javaFileCount: Int?,
    val runBuild: Boolean?,
    val benchmarkId: String? = null,
)

/**
 * Static J2K conversion metadata available to the evaluator.
 */
data class ConversionEvaluation(
    val available: Boolean,
    val status: String,
    val sourceJavaFileCount: Int?,
    val generatedKotlinFileCount: Int?,
    val warningCount: Int,
    val errorCount: Int,
    val warnings: List<String>,
    val errors: List<String>,
    val benchmarkId: String? = null,
)

/**
 * File-level conversion completeness metrics.
 */
data class FileCoverageMetrics(
    val javaFileCount: Int,
    val kotlinFileCount: Int,
    val matchedKotlinFileCount: Int,
    val missingKotlinFiles: List<String>,
    val unexpectedKotlinFiles: List<String>,
    val emptyGeneratedFiles: List<String>,
    val packagePreservedCount: Int,
    val packageMismatchFiles: List<String>,
) {
    /**
     * Percentage of Java inputs with a matching generated Kotlin file.
     */
    val coveragePercent: Double =
        if (javaFileCount == 0) {
            0.0
        } else {
            matchedKotlinFileCount.toDouble() * PERCENT_SCALE / javaFileCount.toDouble()
        }

    /**
     * Percentage of matched outputs preserving Java package declarations.
     */
    val packagePreservationPercent: Double =
        if (matchedKotlinFileCount == 0) {
            0.0
        } else {
            packagePreservedCount.toDouble() * PERCENT_SCALE / matchedKotlinFileCount.toDouble()
        }

    private companion object {
        private const val PERCENT_SCALE = 100.0
    }
}

/**
 * Lightweight structural preservation metrics.
 */
data class StructuralMetrics(
    val javaTopLevelDeclarationCount: Int,
    val kotlinTopLevelDeclarationCount: Int,
    val javaClassLikeCount: Int,
    val kotlinClassLikeCount: Int,
    val javaInterfaceCount: Int,
    val kotlinInterfaceCount: Int,
    val javaEnumCount: Int,
    val kotlinEnumCount: Int,
    val javaMethodCount: Int,
    val kotlinFunctionCount: Int,
    val publicApiNameOverlapCount: Int,
    val missingPublicApiNames: List<String>,
    val kotlinOnlyPublicApiNames: List<String>,
    val nameDiffs: StructuralNameDiffs,
)

/**
 * Grouped structural name differences between Java input and generated Kotlin output.
 */
data class StructuralNameDiffs(
    val classLike: StructuralNameDiff,
    val interfaces: StructuralNameDiff,
    val enums: StructuralNameDiff,
    val objects: StructuralNameDiff,
    val classLikeToObjectNames: List<String>,
    val javaBeanAccessorNames: List<String>,
    val functions: StructuralNameDiff,
)

/**
 * One structural-name diff category.
 */
data class StructuralNameDiff(
    val missingInKotlin: List<String>,
    val kotlinOnly: List<String>,
)

/**
 * Kotlin quality-review metrics and warning findings.
 */
data class QualityMetrics(
    val todoCount: Int,
    val notNullAssertionCount: Int,
    val notNullAssertionInCallCount: Int,
    val anyNullableCount: Int,
    val unresolvedImportCount: Int,
    val javaInteropReferenceCount: Int,
    val getterSetterCallCount: Int,
    val nullableBooleanComparisonCount: Int,
    val eagerPropertyInitializationCount: Int,
    val findings: List<EvaluationWarning>,
)
