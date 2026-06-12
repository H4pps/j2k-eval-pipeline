package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.evaluation.metrics.ContentMetricsCalculator
import iurii.bulanov.benchmark.evaluation.metrics.EvaluationSourceMatcher
import iurii.bulanov.benchmark.evaluation.metrics.FileCoverageCalculator
import iurii.bulanov.benchmark.evaluation.metrics.MatchedSourceMetricsCalculator
import iurii.bulanov.benchmark.evaluation.metrics.NullabilityMetricsCalculator
import iurii.bulanov.benchmark.evaluation.metrics.QualityMetricsCalculator
import iurii.bulanov.benchmark.evaluation.metrics.StructuralMetricsCalculator
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path

/**
 * Calculates deterministic evaluator metrics from discovered Java and Kotlin files.
 */
class EvaluationMetricsCalculator(
    private val scanner: SourceTextScanner = SourceTextScanner(),
) {
    private val sourceMatcher = EvaluationSourceMatcher(scanner)
    private val fileCoverageCalculator = FileCoverageCalculator(scanner)
    private val structuralMetricsCalculator: MatchedSourceMetricsCalculator<StructuralMetrics> = StructuralMetricsCalculator()
    private val contentMetricsCalculator: MatchedSourceMetricsCalculator<ContentMetrics> = ContentMetricsCalculator()
    private val nullabilityMetricsCalculator: MatchedSourceMetricsCalculator<NullabilityMetrics> = NullabilityMetricsCalculator()
    private val qualityMetricsCalculator = QualityMetricsCalculator(scanner)

    /**
     * Calculates file coverage, structural preservation, and Kotlin quality metrics.
     */
    fun calculate(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        sourceRoots: List<String>,
    ): EvaluationMetrics {
        val pathIndex = sourceMatcher.sourcePathIndex(javaFiles, kotlinFiles, sourceRoots)
        val matchedStructures = sourceMatcher.matchedStructures(pathIndex)
        val qualityFiles = qualityMetricsCalculator.scanQuality(kotlinFiles)
        val structure = structuralMetricsCalculator.calculate(matchedStructures)

        return EvaluationMetrics(
            fileCoverage = fileCoverageCalculator.calculate(javaFiles, kotlinFiles, pathIndex),
            structure = structure,
            content = contentMetricsCalculator.calculate(matchedStructures),
            nullability = nullabilityMetricsCalculator.calculate(matchedStructures),
            quality = qualityMetricsCalculator.calculate(qualityFiles),
        )
    }

    /**
     * Maps a checkout-relative Java path to the generated-output-relative Kotlin path.
     */
    fun expectedKotlinRelativePath(
        javaRelativePath: Path,
        sourceRoots: List<String>,
    ): Path = sourceMatcher.expectedKotlinRelativePath(javaRelativePath, sourceRoots)
}

/**
 * Grouped metric families calculated by the evaluator.
 */
data class EvaluationMetrics(
    val fileCoverage: FileCoverageMetrics,
    val structure: StructuralMetrics,
    val content: ContentMetrics,
    val nullability: NullabilityMetrics,
    val quality: QualityMetrics,
)
