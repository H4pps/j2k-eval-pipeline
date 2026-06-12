package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.EvaluationWarning
import iurii.bulanov.benchmark.evaluation.NullabilityMetrics
import iurii.bulanov.benchmark.evaluation.SourceNullabilityProfile
import iurii.bulanov.benchmark.evaluation.SourceStructure

/**
 * Calculates parser-backed Java annotation to Kotlin nullable-type preservation metrics.
 */
internal class NullabilityMetricsCalculator : MatchedSourceMetricsCalculator<NullabilityMetrics> {
    /**
     * Calculates nullability preservation and Kotlin nullability-operation consistency metrics.
     */
    override fun calculate(pairs: List<MatchedSourceStructure>): NullabilityMetrics {
        val contradictoryNullabilityPatterns = pairs.sumOf { it.kotlin.nullability.contradictoryNullabilityPatternCount }
        val nullComparisonCount = pairs.sumOf { it.kotlin.nullability.nullComparisonCount }
        val nullabilityCastCount = pairs.sumOf { it.kotlin.nullability.nullabilityCastCount }
        val safeCallCount = pairs.sumOf { it.kotlin.nullability.safeCallCount }
        val totalNullabilityOperationCount = pairs.sumOf { it.kotlin.nullability.totalNullabilityOperationCount }
        val nullableNotPreserved =
            pairs
                .flatMap { pair ->
                    pair.java.nullability.nullableNames
                        .filterNot { name -> pair.java.nullabilityNameMapsToKotlinNullable(name, pair.kotlin.nullability) }
                        .map { name -> "${pair.path}$MEMBER_PATH_SEPARATOR$name" }
                }.sorted()
        val notNullBecameNullable =
            pairs
                .flatMap { pair ->
                    pair.java.nullability.notNullNames
                        .filter { name -> pair.java.nullabilityNameMapsToKotlinNullable(name, pair.kotlin.nullability) }
                        .map { name -> "${pair.path}$MEMBER_PATH_SEPARATOR$name" }
                }.sorted()

        return NullabilityMetrics(
            javaNullableAnnotationCount = pairs.sumOf { it.java.nullability.nullableAnnotationCount },
            javaNotNullAnnotationCount = pairs.sumOf { it.java.nullability.notNullAnnotationCount },
            kotlinNullableTypeCount = pairs.sumOf { it.kotlin.nullability.nullableTypeNames.size },
            contradictoryNullabilityPatterns = contradictoryNullabilityPatterns,
            nullComparisonCount = nullComparisonCount,
            nullabilityCastCount = nullabilityCastCount,
            safeCallCount = safeCallCount,
            totalNullabilityOperationCount = totalNullabilityOperationCount,
            nullabilityInferenceAccuracy =
                nullabilityInferenceAccuracy(
                    contradictoryPatterns = contradictoryNullabilityPatterns,
                    totalNullabilityOperations = totalNullabilityOperationCount,
                ),
            nullableAnnotationsNotPreserved = nullableNotPreserved,
            notNullAnnotationsBecameNullable = notNullBecameNullable,
            findings = nullabilityFindings(nullableNotPreserved, notNullBecameNullable),
        )
    }

    private fun SourceStructure.nullabilityNameMapsToKotlinNullable(
        name: String,
        kotlinNullability: SourceNullabilityProfile,
    ): Boolean =
        name in kotlinNullability.nullableTypeNames ||
            javaBeanAccessorPropertyNames[name].orEmpty().any { it in kotlinNullability.nullableTypeNames }

    private fun nullabilityInferenceAccuracy(
        contradictoryPatterns: Int,
        totalNullabilityOperations: Int,
    ): Double =
        if (totalNullabilityOperations == 0) {
            PERFECT_PRESERVATION
        } else {
            PERFECT_PRESERVATION - contradictoryPatterns.toDouble() / totalNullabilityOperations.toDouble()
        }

    private fun nullabilityFindings(
        nullableNotPreserved: List<String>,
        notNullBecameNullable: List<String>,
    ): List<EvaluationWarning> =
        buildList {
            nullableNotPreserved
                .groupingBy { it.substringBefore(MEMBER_PATH_SEPARATOR) }
                .eachCount()
                .forEach { (path, count) ->
                    add(
                        EvaluationWarning(
                            code = "nullable_annotation_not_preserved",
                            message = "Java nullable annotations were not reflected as nullable Kotlin declarations.",
                            path = path,
                            count = count,
                        ),
                    )
                }
            notNullBecameNullable
                .groupingBy { it.substringBefore(MEMBER_PATH_SEPARATOR) }
                .eachCount()
                .forEach { (path, count) ->
                    add(
                        EvaluationWarning(
                            code = "not_null_annotation_became_nullable",
                            message = "Java not-null annotations were converted to nullable Kotlin declarations.",
                            path = path,
                            count = count,
                        ),
                    )
                }
        }

    private companion object {
        private const val MEMBER_PATH_SEPARATOR = "#"
        private const val PERFECT_PRESERVATION = 1.0
    }
}
