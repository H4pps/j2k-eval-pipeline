package iurii.bulanov.benchmark.checkout

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Writes deterministic machine-readable reports for benchmark checkout runs.
 */
class CheckoutReportWriter(
    private val logger: StructuredLogger = JsonLineLogger(),
) {
    /**
     * Writes [result] to either [reportPath] or the default benchmark checkout report path.
     */
    fun write(
        result: CheckoutResult,
        runBuild: Boolean,
        reportPath: Path?,
    ): Path {
        val resolvedPath = resolveReportPath(result.config, reportPath)
        resolvedPath.parent?.createDirectories()
        resolvedPath.writeText(renderJson(result, runBuild))
        logger.info("checkout_report_written", mapOf("path" to resolvedPath.toString()))
        return resolvedPath
    }

    /**
     * Resolves the configured checkout report path.
     */
    fun resolveReportPath(
        config: BenchmarkConfig,
        overridePath: Path?,
    ): Path = (overridePath ?: defaultReportPath(config)).normalize()

    /**
     * Returns the default checkout report path for [config].
     */
    fun defaultReportPath(config: BenchmarkConfig): Path = Path.of("build", "benchmarks", config.id, "checkout.json")

    /**
     * Renders [result] as deterministic JSON.
     */
    fun renderJson(
        result: CheckoutResult,
        runBuild: Boolean,
    ): String =
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
                "checkout_directory" to result.config.checkout.directory,
                "java_file_count" to result.javaFileCount,
                "build_status" to result.buildStatus.name.lowercase(),
                "run_build" to runBuild,
            ),
        ) + "\n"
}
