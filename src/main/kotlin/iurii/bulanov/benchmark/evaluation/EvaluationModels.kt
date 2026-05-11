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
)

/**
 * Minimal Phase 2 evaluation result used by JSON and Markdown reports.
 */
data class EvaluationResult(
    val config: BenchmarkConfig,
    val checkoutDirectory: Path,
    val generatedKotlinDirectory: Path,
    val reportDirectory: Path,
    val javaFiles: List<DiscoveredSourceFile>,
    val kotlinFiles: List<DiscoveredSourceFile>,
    val status: EvaluationStatus,
    val warnings: List<EvaluationWarning>,
)
