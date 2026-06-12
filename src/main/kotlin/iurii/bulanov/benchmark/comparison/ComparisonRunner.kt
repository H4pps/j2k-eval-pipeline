package iurii.bulanov.benchmark.comparison

import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Compiles the per-kind evaluation reports for one benchmark into a single comparison.
 *
 * Reads `<reportDirectory>/<kind>/evaluation.json` for each requested kind, tolerating absent kinds
 * (so a partial run still produces a comparison), and writes `comparison.{json,md}` into the
 * benchmark's report directory.
 */
class ComparisonRunner(
    private val logger: StructuredLogger = JsonLineLogger(),
    private val configParser: BenchmarkConfigParser = BenchmarkConfigParser(),
    private val reader: ComparisonReportReader = ComparisonReportReader(),
    private val reportWriter: ComparisonReportWriter = ComparisonReportWriter(logger = logger),
) {
    /**
     * Compiles the comparison for [request] and returns a process-style exit code.
     */
    fun run(request: ComparisonRequest): Int {
        logger.info(
            "comparison_started",
            mapOf("config_path" to request.configPath.toString(), "kinds" to request.kinds.map { it.id }),
        )
        return try {
            val config = configParser.parse(request.configPath)
            val reportDirectory = request.reportDirectory ?: Path.of("build", "reports", "j2k-eval", config.id)
            val result = reader.read(reportDirectory, request.kinds)
            val comparison =
                Comparison(
                    benchmarkId = config.id,
                    benchmarkName = config.name,
                    kinds = result.present,
                    missingKinds = result.missing,
                )
            reportWriter.write(comparison, reportDirectory)
            request.githubSummaryPath?.let { writeGitHubSummary(it, reportDirectory) }
            logger.info(
                "comparison_completed",
                mapOf(
                    "id" to config.id,
                    "compared_kinds" to result.present.map { it.kind.id },
                    "missing_kinds" to result.missing.map { it.id },
                ),
            )
            0
        } catch (exception: Exception) {
            logger.error("comparison_failed", mapOf("error" to (exception.message ?: exception::class.simpleName)))
            1
        }
    }

    /**
     * Appends the generated comparison Markdown to the GitHub Actions step summary.
     */
    private fun writeGitHubSummary(
        path: Path,
        reportDirectory: Path,
    ) {
        if (!path.exists()) {
            Files.createDirectories(path.parent ?: Path.of("."))
            path.writeText("")
        }
        val summaryPath = reportDirectory.resolve("comparison.md")
        path.appendText(
            buildString {
                appendLine()
                appendLine("---")
                appendLine()
                append(summaryPath.readText())
            },
        )
        logger.info("github_summary_written", mapOf("path" to path.toString(), "source" to summaryPath.toString()))
    }
}

/**
 * Parser-independent request for compiling a multi-kind comparison.
 */
data class ComparisonRequest(
    val configPath: Path,
    val kinds: List<ConverterKind>,
    val reportDirectory: Path? = null,
    val githubSummaryPath: Path? = null,
)
