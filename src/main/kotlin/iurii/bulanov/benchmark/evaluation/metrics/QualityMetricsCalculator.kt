package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.QualityFileMetrics
import iurii.bulanov.benchmark.evaluation.QualityMetrics
import iurii.bulanov.benchmark.evaluation.SourceTextScanner
import iurii.bulanov.source.DiscoveredSourceFile
import kotlin.io.path.readText

/**
 * Scans and aggregates generated Kotlin quality warning metrics.
 */
internal class QualityMetricsCalculator(
    private val scanner: SourceTextScanner,
) {
    /**
     * Scans quality warnings for generated Kotlin files.
     */
    fun scanQuality(kotlinFiles: List<DiscoveredSourceFile>): List<QualityFileMetrics> =
        kotlinFiles.map { file ->
            scanner.scanKotlinQuality(
                path = file.relativePath.normalize().toString(),
                source = file.absolutePath.readText(),
            )
        }

    /**
     * Aggregates per-file Kotlin quality warning metrics.
     */
    fun calculate(qualityFiles: List<QualityFileMetrics>): QualityMetrics =
        QualityMetrics(
            todoCount = qualityFiles.sumOf { it.todoCount },
            notNullAssertionCount = qualityFiles.sumOf { it.notNullAssertionCount },
            notNullAssertionInCallCount = qualityFiles.sumOf { it.notNullAssertionInCallCount },
            anyNullableCount = qualityFiles.sumOf { it.anyNullableCount },
            unresolvedImportCount = qualityFiles.sumOf { it.unresolvedImportCount },
            javaInteropReferenceCount = qualityFiles.sumOf { it.javaInteropReferenceCount },
            getterSetterCallCount = qualityFiles.sumOf { it.getterSetterCallCount },
            nullableBooleanComparisonCount = qualityFiles.sumOf { it.nullableBooleanComparisonCount },
            eagerPropertyInitializationCount = qualityFiles.sumOf { it.eagerPropertyInitializationCount },
            findings = qualityFiles.flatMap { it.findings },
        )
}
