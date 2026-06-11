package iurii.bulanov.benchmark.evaluation.reporting

import iurii.bulanov.benchmark.evaluation.EvaluationResult

/**
 * Renderer contract for one evaluator report representation.
 */
internal interface EvaluationReportRenderer {
    /**
     * Renders an evaluator result into a deterministic report body.
     */
    fun render(result: EvaluationResult): String
}
