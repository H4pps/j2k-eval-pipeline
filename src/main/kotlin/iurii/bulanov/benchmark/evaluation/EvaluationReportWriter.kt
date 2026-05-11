package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * Writes Phase 2 evaluator reports to deterministic JSON and Markdown files.
 */
class EvaluationReportWriter(
    private val logger: StructuredLogger = JsonLineLogger(),
) {
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
     * Renders the machine-readable Phase 2 report body.
     */
    fun renderJson(result: EvaluationResult): String =
        JsonEncoder.encode(
            linkedMapOf(
                "benchmark" to
                    linkedMapOf(
                        "id" to result.config.id,
                        "name" to result.config.name,
                        "role" to result.config.role,
                        "repository_source" to result.config.repository.source,
                        "repository_upstream" to result.config.repository.upstream,
                        "repository_ref" to result.config.repository.ref,
                    ),
                "paths" to
                    linkedMapOf(
                        "checkout_directory" to result.checkoutDirectory.toString(),
                        "generated_kotlin_directory" to result.generatedKotlinDirectory.toString(),
                        "report_directory" to result.reportDirectory.toString(),
                    ),
                "counts" to
                    linkedMapOf(
                        "java_file_count" to result.javaFiles.size,
                        "kotlin_file_count" to result.kotlinFiles.size,
                        "warning_count" to result.warnings.size,
                    ),
                "status" to result.status.name.lowercase(),
                "warnings" to
                    result.warnings.map { warning ->
                        linkedMapOf(
                            "code" to warning.code,
                            "message" to warning.message,
                            "path" to warning.path,
                        )
                    },
            ),
        ) + "\n"

    /**
     * Renders the human-readable Phase 2 summary report body.
     */
    fun renderMarkdown(result: EvaluationResult): String =
        buildString {
            appendLine("# J2K Evaluation Summary")
            appendLine()
            appendLine("## Benchmark")
            appendLine()
            appendLine("- ID: `${result.config.id}`")
            appendLine("- Name: `${result.config.name}`")
            appendLine("- Role: `${result.config.role}`")
            appendLine("- Repository source: `${result.config.repository.source}`")
            appendLine("- Pinned ref: `${result.config.repository.ref}`")
            appendLine()
            appendLine("## Paths")
            appendLine()
            appendLine("- Checkout directory: `${result.checkoutDirectory}`")
            appendLine("- Generated Kotlin directory: `${result.generatedKotlinDirectory}`")
            appendLine("- Report directory: `${result.reportDirectory}`")
            appendLine()
            appendLine("## Counts")
            appendLine()
            appendLine("- Java files discovered: `${result.javaFiles.size}`")
            appendLine("- Kotlin files discovered: `${result.kotlinFiles.size}`")
            appendLine("- Warnings: `${result.warnings.size}`")
            appendLine("- Status: `${result.status.name.lowercase()}`")
            appendLine()
            appendLine("## Warnings")
            appendLine()
            if (result.warnings.isEmpty()) {
                appendLine("- None")
            } else {
                result.warnings.forEach { warning ->
                    val pathSuffix = warning.path?.let { " `$it`" }.orEmpty()
                    appendLine("- `${warning.code}`$pathSuffix: ${warning.message}")
                }
            }
        }
}
