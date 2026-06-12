package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.evaluation.reporting.EvaluationReportJsonRenderer
import iurii.bulanov.benchmark.evaluation.reporting.EvaluationReportMarkdownRenderer
import iurii.bulanov.benchmark.evaluation.reporting.EvaluationReportRenderer
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * Writes evaluator reports to deterministic JSON and Markdown files.
 */
class EvaluationReportWriter(
    private val logger: StructuredLogger = JsonLineLogger(),
) {
    private val jsonRenderer: EvaluationReportRenderer = EvaluationReportJsonRenderer()
    private val markdownRenderer: EvaluationReportRenderer = EvaluationReportMarkdownRenderer()

    /**
     * Writes `evaluation.json` and `summary.md` under the result report directory.
     */
    fun write(result: EvaluationResult) {
        Files.createDirectories(result.reportDirectory)
        val jsonPath = result.reportDirectory.resolve("evaluation.json")
        val summaryPath = result.reportDirectory.resolve("summary.md")

        jsonPath.writeText(renderJson(result))
        summaryPath.writeText(renderMarkdown(result))

        logger.info(
            "evaluation_reports_written",
            mapOf("json_path" to jsonPath.toString(), "summary_path" to summaryPath.toString()),
        )
    }

    /**
     * Renders the machine-readable evaluator report body.
     */
    fun renderJson(result: EvaluationResult): String = jsonRenderer.render(result)

    /**
     * Renders the human-readable evaluator summary report body.
     */
    fun renderMarkdown(result: EvaluationResult): String = markdownRenderer.render(result)
}
