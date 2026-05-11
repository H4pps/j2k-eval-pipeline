package iurii.bulanov.benchmark.checkout

import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Runs benchmark checkout execution logic independently from CLI parsing concerns.
 *
 * Structured logging is constructor-injected so one logger instance can be shared
 * across CLI and validator layers.
 */
class BenchmarkCheckoutRunner(
    private val logger: StructuredLogger = JsonLineLogger(),
    private val configParser: BenchmarkConfigParser = BenchmarkConfigParser(),
    private val checkoutValidator: CheckoutValidator = CheckoutValidator(logger = logger),
) {
    /**
     * Executes checkout validation for [request] and returns a process-style exit code.
     */
    fun run(request: CheckoutBenchmarkRequest): Int {
        logger.info(
            "benchmark_checkout_started",
            mapOf("config_path" to request.configPath.toString(), "run_build" to request.runBuild),
        )
        return try {
            val config = configParser.parse(request.configPath)
            logger.info(
                "benchmark_config_parsed",
                mapOf(
                    "config_path" to request.configPath.toString(),
                    "id" to config.id,
                    "name" to config.name,
                    "role" to config.role,
                    "repo_source" to config.repository.source,
                    "repo_ref" to config.repository.ref,
                    "checkout_dir" to config.checkout.directory,
                    "java_roots_count" to config.java.sourceRoots.size,
                    "build_commands_count" to config.build.commands.size,
                ),
            )

            val result = checkoutValidator.validate(config, request.runBuild)
            request.githubSummaryPath?.let { writeGitHubSummary(it, request.configPath, result) }
            request.githubOutputPath?.let { writeGitHubOutput(it, result) }

            logger.info(
                "benchmark_checkout_completed",
                mapOf(
                    "id" to result.config.id,
                    "java_file_count" to result.javaFileCount,
                    "build_status" to result.buildStatus.name.lowercase(),
                ),
            )
            0
        } catch (exception: Exception) {
            val errorMessage = exception.message ?: exception::class.simpleName.orEmpty()
            request.githubSummaryPath?.let { writeGitHubFailureSummarySafely(it, request.configPath, errorMessage) }
            logger.error("benchmark_checkout_failed", mapOf("error" to errorMessage))
            1
        }
    }

    /**
     * Writes GitHub Step Summary markdown for successful checkout sanity checks.
     */
    private fun writeGitHubSummary(
        path: Path,
        configPath: Path,
        result: CheckoutResult,
    ) {
        ensureWritableFile(path)
        val summary =
            buildString {
                appendLine("## Benchmark Checkout")
                appendLine("- Config path: `$configPath`")
                appendLine("- ID: `${result.config.id}`")
                appendLine("- Name: `${result.config.name}`")
                appendLine("- Role: `${result.config.role}`")
                appendLine("- Repository source: `${result.config.repository.source}`")
                appendLine("- Repository upstream: `${result.config.repository.upstream}`")
                appendLine("- Pinned ref: `${result.config.repository.ref}`")
                appendLine("- Branch: `${result.config.repository.branch}`")
                appendLine("- Checkout directory: `${result.config.checkout.directory}`")
                appendLine("- Source roots: `${result.config.java.sourceRoots.joinToString(", ")}`")
                appendLine("- Java file count: `${result.javaFileCount}`")
                appendLine("- Build status: `${result.buildStatus.name.lowercase()}`")
            }
        path.appendText(summary)
        logger.info("github_summary_written", mapOf("path" to path.toString()))
    }

    /**
     * Writes GitHub Step Summary markdown for failed checkout sanity checks.
     */
    private fun writeGitHubFailureSummary(
        path: Path,
        configPath: Path,
        error: String,
    ) {
        ensureWritableFile(path)
        val summary =
            buildString {
                appendLine("## Benchmark Checkout")
                appendLine("- Config path: `$configPath`")
                appendLine("- Status: `failed`")
                appendLine("- Error: `$error`")
            }
        path.appendText(summary)
        logger.info("github_summary_written", mapOf("path" to path.toString(), "status" to "failed"))
    }

    /**
     * Writes failure summary output and logs a structured error when this write itself fails.
     */
    private fun writeGitHubFailureSummarySafely(
        path: Path,
        configPath: Path,
        error: String,
    ) {
        runCatching { writeGitHubFailureSummary(path, configPath, error) }
            .onFailure { summaryException ->
                logger.error(
                    "github_summary_failed",
                    mapOf("path" to path.toString(), "error" to (summaryException.message ?: summaryException::class.simpleName)),
                )
            }
    }

    /**
     * Writes benchmark metadata and checkout results to a GitHub output file.
     */
    private fun writeGitHubOutput(
        path: Path,
        result: CheckoutResult,
    ) {
        ensureWritableFile(path)
        val outputs =
            linkedMapOf(
                "id" to result.config.id,
                "name" to result.config.name,
                "role" to result.config.role,
                "repo_source" to result.config.repository.source,
                "repo_upstream" to result.config.repository.upstream,
                "repo_ref" to result.config.repository.ref,
                "repo_branch" to result.config.repository.branch,
                "checkout_dir" to result.config.checkout.directory,
                "java_roots" to
                    result.config.java.sourceRoots
                        .joinToString(", "),
                "java_file_count" to result.javaFileCount.toString(),
                "build_status" to result.buildStatus.name.lowercase(),
            )

        val payload = outputs.entries.joinToString(separator = "\n", postfix = "\n") { "${it.key}=${it.value}" }
        path.appendText(payload)
        logger.info("github_output_written", mapOf("path" to path.toString(), "keys" to outputs.keys.toList()))
    }

    /**
     * Ensures that a file and its parent directory exist before appending text.
     */
    private fun ensureWritableFile(path: Path) {
        if (!path.exists()) {
            Files.createDirectories(path.parent ?: Path.of("."))
            path.writeText("")
        }
    }
}

/**
 * Parser-agnostic request payload for the benchmark checkout execution runner.
 */
data class CheckoutBenchmarkRequest(
    val configPath: Path,
    val runBuild: Boolean,
    val githubSummaryPath: Path?,
    val githubOutputPath: Path?,
)
