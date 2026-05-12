package iurii.bulanov.benchmark.conversion

import iurii.bulanov.logging.JsonEncoder
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Writes deterministic machine-readable reports for static J2K conversion runs.
 */
class ConversionReportWriter {
    /**
     * Writes [report] to its configured `conversion.json` path.
     */
    fun write(report: ConversionReport) {
        report.paths.conversionReport.parent
            ?.createDirectories()
        report.paths.logsDirectory.createDirectories()
        report.paths.conversionReport.writeText(renderJson(report))
    }

    /**
     * Renders [report] as deterministic JSON.
     */
    fun renderJson(report: ConversionReport): String =
        JsonEncoder.encode(
            linkedMapOf(
                "benchmark" to
                    linkedMapOf(
                        "id" to report.config.id,
                        "name" to report.config.name,
                        "role" to report.config.role,
                        "repository_source" to report.config.repository.source,
                        "repository_ref" to report.config.repository.ref,
                    ),
                "status" to report.status.name.lowercase(),
                "counts" to
                    linkedMapOf(
                        "source_java_file_count" to report.sourceJavaFileCount,
                        "generated_kotlin_file_count" to report.generatedKotlinFileCount,
                        "warning_count" to report.warnings.size,
                        "error_count" to report.errors.size,
                    ),
                "paths" to
                    linkedMapOf(
                        "staging_directory" to report.paths.stagingDirectory.toString(),
                        "generated_kotlin_directory" to report.paths.generatedKotlinDirectory.toString(),
                        "conversion_report" to report.paths.conversionReport.toString(),
                        "logs_directory" to report.paths.logsDirectory.toString(),
                    ),
                "converter" to
                    linkedMapOf(
                        "command" to report.converterCommand,
                    ),
                "warnings" to report.warnings,
                "errors" to report.errors,
            ),
        ) + "\n"

    /**
     * Writes a best-effort failure report for infrastructure failures.
     */
    fun writeFailure(report: ConversionReport) {
        report.paths.conversionReport.parent
            ?.let { Files.createDirectories(it) }
        report.paths.conversionReport.writeText(renderJson(report))
    }
}
