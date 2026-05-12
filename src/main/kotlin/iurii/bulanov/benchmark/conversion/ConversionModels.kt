package iurii.bulanov.benchmark.conversion

import iurii.bulanov.benchmark.config.BenchmarkConfig
import java.nio.file.Path

/**
 * Optional path overrides accepted by the J2K conversion runner.
 */
data class ConversionPathOverrides(
    val stagingDirectory: Path?,
    val generatedKotlinDirectory: Path?,
    val conversionReport: Path?,
    val logsDirectory: Path?,
)

/**
 * Resolved filesystem paths for one benchmark conversion run.
 */
data class ConversionPaths(
    val stagingDirectory: Path,
    val generatedKotlinDirectory: Path,
    val conversionReport: Path,
    val logsDirectory: Path,
)

/**
 * Result of staging a benchmark checkout into a disposable conversion directory.
 */
data class SourceStagingResult(
    val sourceDirectory: Path,
    val stagingDirectory: Path,
    val copiedFileCount: Int,
)

/**
 * Result of collecting Kotlin files from staged converter output.
 */
data class GeneratedKotlinCollectionResult(
    val generatedDirectory: Path,
    val generatedFiles: List<Path>,
    val warnings: List<String>,
)

/**
 * High-level conversion status emitted to `conversion.json`.
 */
enum class ConversionStatus {
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    PARTIAL,
    FAILED,
}

/**
 * Machine-readable summary of one J2K conversion run.
 */
data class ConversionReport(
    val config: BenchmarkConfig,
    val status: ConversionStatus,
    val sourceJavaFileCount: Int,
    val generatedKotlinFileCount: Int,
    val paths: ConversionPaths,
    val converterCommand: List<String>,
    val warnings: List<String>,
    val errors: List<String>,
)
