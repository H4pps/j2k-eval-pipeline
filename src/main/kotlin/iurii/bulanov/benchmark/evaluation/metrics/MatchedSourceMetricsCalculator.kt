package iurii.bulanov.benchmark.evaluation.metrics

/**
 * Calculator contract for metrics derived from matched Java/Kotlin source pairs.
 */
internal interface MatchedSourceMetricsCalculator<T> {
    /**
     * Calculates one metric family from matched parser structures.
     */
    fun calculate(pairs: List<MatchedSourceStructure>): T
}
