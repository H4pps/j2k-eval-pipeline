package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.util.Locale
import kotlin.io.path.writeText

/**
 * Writes evaluator reports to deterministic JSON and Markdown files.
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
     * Renders the machine-readable evaluator report body.
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
                        "conversion_report" to result.conversionReportPath.toString(),
                        "checkout_report" to result.checkoutReportPath.toString(),
                        "report_directory" to result.reportDirectory.toString(),
                    ),
                "checkout" to
                    linkedMapOf(
                        "available" to result.checkout.available,
                        "benchmark_id" to result.checkout.benchmarkId,
                        "build_status" to result.checkout.buildStatus,
                        "java_file_count" to result.checkout.javaFileCount,
                        "run_build" to result.checkout.runBuild,
                    ),
                "conversion" to
                    linkedMapOf(
                        "available" to result.conversion.available,
                        "benchmark_id" to result.conversion.benchmarkId,
                        "status" to result.conversion.status,
                        "source_java_file_count" to result.conversion.sourceJavaFileCount,
                        "generated_kotlin_file_count" to result.conversion.generatedKotlinFileCount,
                        "warning_count" to result.conversion.warningCount,
                        "error_count" to result.conversion.errorCount,
                        "warnings" to result.conversion.warnings,
                        "errors" to result.conversion.errors,
                    ),
                "file_coverage" to fileCoverageJson(result.fileCoverage),
                "structure" to structureJson(result.structure),
                "quality" to qualityJson(result.quality),
                "counts" to
                    linkedMapOf(
                        "java_file_count" to result.fileCoverage.javaFileCount,
                        "kotlin_file_count" to result.fileCoverage.kotlinFileCount,
                        "warning_count" to result.warnings.size,
                    ),
                "status" to result.status.name.lowercase(),
                "warnings" to
                    result.warnings.map { warning ->
                        linkedMapOf(
                            "code" to warning.code,
                            "message" to warning.message,
                            "path" to warning.path,
                            "count" to warning.count,
                        )
                    },
            ),
        ) + "\n"

    /**
     * Renders the human-readable evaluator summary report body.
     */
    fun renderMarkdown(result: EvaluationResult): String =
        buildString {
            appendLine("# J2K Evaluation Summary")
            appendBenchmark(result)
            appendPaths(result)
            appendConversion(result.conversion)
            appendCheckout(result.checkout)
            appendFileCoverage(result.fileCoverage)
            appendStructure(result.structure)
            appendQuality(result.quality)
            appendNotableFailures(result.conversion.errors)
            appendWarnings(result)
        }

    /**
     * Appends benchmark metadata to the Markdown summary.
     */
    private fun StringBuilder.appendBenchmark(result: EvaluationResult) {
        appendLine()
        appendLine("## Benchmark")
        appendLine()
        appendLine("- ID: `${result.config.id}`")
        appendLine("- Name: `${result.config.name}`")
        appendLine("- Role: `${result.config.role}`")
        appendLine("- Repository source: `${result.config.repository.source}`")
        appendLine("- Pinned ref: `${result.config.repository.ref}`")
    }

    /**
     * Appends filesystem paths used by the evaluator.
     */
    private fun StringBuilder.appendPaths(result: EvaluationResult) {
        appendLine()
        appendLine("## Paths")
        appendLine()
        appendLine("- Checkout directory: `${result.checkoutDirectory}`")
        appendLine("- Generated Kotlin directory: `${result.generatedKotlinDirectory}`")
        appendLine("- Conversion report: `${result.conversionReportPath}`")
        appendLine("- Checkout report: `${result.checkoutReportPath}`")
        appendLine("- Report directory: `${result.reportDirectory}`")
    }

    /**
     * Appends conversion execution metadata.
     */
    private fun StringBuilder.appendConversion(conversion: ConversionEvaluation) {
        appendLine()
        appendLine("## Conversion Execution")
        appendLine()
        appendLine("- Available: `${conversion.available}`")
        appendLine("- Status: `${conversion.status}`")
        appendLine("- Source Java files: `${conversion.sourceJavaFileCount ?: "unknown"}`")
        appendLine("- Generated Kotlin files: `${conversion.generatedKotlinFileCount ?: "unknown"}`")
        appendLine("- Converter warnings: `${conversion.warningCount}`")
        appendLine("- Converter errors: `${conversion.errorCount}`")
    }

    /**
     * Appends original checkout/build metadata.
     */
    private fun StringBuilder.appendCheckout(checkout: CheckoutEvaluation) {
        appendLine()
        appendLine("## Original Build")
        appendLine()
        appendLine("- Checkout report available: `${checkout.available}`")
        appendLine("- Build status: `${checkout.buildStatus}`")
        appendLine("- Run build: `${checkout.runBuild ?: "unknown"}`")
    }

    /**
     * Appends file coverage metrics.
     */
    private fun StringBuilder.appendFileCoverage(fileCoverage: FileCoverageMetrics) {
        appendLine()
        appendLine("## File Coverage")
        appendLine()
        appendLine("- Java files discovered: `${fileCoverage.javaFileCount}`")
        appendLine("- Kotlin files discovered: `${fileCoverage.kotlinFileCount}`")
        appendLine("- Matched Kotlin files: `${fileCoverage.matchedKotlinFileCount}`")
        appendLine("- Coverage: `${formatPercent(fileCoverage.coveragePercent)}%`")
        appendLine("- Missing Kotlin files: `${fileCoverage.missingKotlinFiles.size}`")
        appendLine("- Unexpected Kotlin files: `${fileCoverage.unexpectedKotlinFiles.size}`")
        appendLine("- Empty generated files: `${fileCoverage.emptyGeneratedFiles.size}`")
        appendLine("- Package preservation: `${formatPercent(fileCoverage.packagePreservationPercent)}%`")
    }

    /**
     * Appends structural preservation metrics.
     */
    private fun StringBuilder.appendStructure(structure: StructuralMetrics) {
        appendLine()
        appendLine("## Structural Preservation")
        appendLine()
        appendLine("- Java declarations: `${structure.javaTopLevelDeclarationCount}`")
        appendLine("- Kotlin declarations: `${structure.kotlinTopLevelDeclarationCount}`")
        appendLine("- Java methods: `${structure.javaMethodCount}`")
        appendLine("- Kotlin functions: `${structure.kotlinFunctionCount}`")
        appendLine("- Public API name overlap: `${structure.publicApiNameOverlapCount}`")
    }

    /**
     * Appends Kotlin quality warning counters.
     */
    private fun StringBuilder.appendQuality(quality: QualityMetrics) {
        appendLine()
        appendLine("## Kotlin Quality Warnings")
        appendLine()
        appendLine("- `TODO()` calls: `${quality.todoCount}`")
        appendLine("- `!!` assertions: `${quality.notNullAssertionCount}`")
        appendLine("- `!!` inside calls: `${quality.notNullAssertionInCallCount}`")
        appendLine("- `Any?` types: `${quality.anyNullableCount}`")
        appendLine("- Unresolved-looking imports: `${quality.unresolvedImportCount}`")
        appendLine("- Java interop leftovers: `${quality.javaInteropReferenceCount}`")
        appendLine("- Getter/setter leftovers: `${quality.getterSetterCallCount}`")
        appendLine("- Nullable boolean comparisons: `${quality.nullableBooleanComparisonCount}`")
        appendLine("- Eager property initializers: `${quality.eagerPropertyInitializationCount}`")
    }

    /**
     * Appends source-level conversion failures reported by J2K.
     */
    private fun StringBuilder.appendNotableFailures(errors: List<String>) {
        appendLine()
        appendLine("## Notable Failures")
        appendLine()
        if (errors.isEmpty()) {
            appendLine("- None")
        } else {
            errors.forEach { error -> appendLine("- `$error`") }
        }
    }

    /**
     * Appends evaluator warnings and final status.
     */
    private fun StringBuilder.appendWarnings(result: EvaluationResult) {
        appendLine()
        appendLine("## Warnings")
        appendLine()
        appendLine("- Warnings: `${result.warnings.size}`")
        appendLine("- Status: `${result.status.name.lowercase()}`")
        appendLine()
        if (result.warnings.isEmpty()) {
            appendLine("- None")
        } else {
            result.warnings.forEach { warning -> appendWarning(warning) }
        }
    }

    /**
     * Appends one evaluator warning row.
     */
    private fun StringBuilder.appendWarning(warning: EvaluationWarning) {
        val pathSuffix = warning.path?.let { " `$it`" }.orEmpty()
        val countSuffix = warning.count?.let { " count=`$it`" }.orEmpty()
        appendLine("- `${warning.code}`$pathSuffix$countSuffix: ${warning.message}")
    }

    /**
     * Formats report percentages with stable decimal separators across locales.
     */
    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f", value)

    /**
     * Renders file coverage metrics to a JSON-compatible map.
     */
    private fun fileCoverageJson(metrics: FileCoverageMetrics): Map<String, Any?> =
        linkedMapOf(
            "java_file_count" to metrics.javaFileCount,
            "kotlin_file_count" to metrics.kotlinFileCount,
            "matched_kotlin_file_count" to metrics.matchedKotlinFileCount,
            "coverage_percent" to metrics.coveragePercent,
            "missing_kotlin_files" to metrics.missingKotlinFiles,
            "unexpected_kotlin_files" to metrics.unexpectedKotlinFiles,
            "empty_generated_files" to metrics.emptyGeneratedFiles,
            "package_preserved_count" to metrics.packagePreservedCount,
            "package_preservation_percent" to metrics.packagePreservationPercent,
            "package_mismatch_files" to metrics.packageMismatchFiles,
        )

    /**
     * Renders structural metrics to a JSON-compatible map.
     */
    private fun structureJson(metrics: StructuralMetrics): Map<String, Any?> =
        linkedMapOf(
            "java_top_level_declaration_count" to metrics.javaTopLevelDeclarationCount,
            "kotlin_top_level_declaration_count" to metrics.kotlinTopLevelDeclarationCount,
            "java_class_like_count" to metrics.javaClassLikeCount,
            "kotlin_class_like_count" to metrics.kotlinClassLikeCount,
            "java_interface_count" to metrics.javaInterfaceCount,
            "kotlin_interface_count" to metrics.kotlinInterfaceCount,
            "java_enum_count" to metrics.javaEnumCount,
            "kotlin_enum_count" to metrics.kotlinEnumCount,
            "java_method_count" to metrics.javaMethodCount,
            "kotlin_function_count" to metrics.kotlinFunctionCount,
            "public_api_name_overlap_count" to metrics.publicApiNameOverlapCount,
            "missing_public_api_names" to metrics.missingPublicApiNames,
        )

    /**
     * Renders Kotlin quality metrics to a JSON-compatible map.
     */
    private fun qualityJson(metrics: QualityMetrics): Map<String, Any?> =
        linkedMapOf(
            "todo_count" to metrics.todoCount,
            "not_null_assertion_count" to metrics.notNullAssertionCount,
            "not_null_assertion_in_call_count" to metrics.notNullAssertionInCallCount,
            "any_nullable_count" to metrics.anyNullableCount,
            "unresolved_import_count" to metrics.unresolvedImportCount,
            "java_interop_reference_count" to metrics.javaInteropReferenceCount,
            "getter_setter_call_count" to metrics.getterSetterCallCount,
            "nullable_boolean_comparison_count" to metrics.nullableBooleanComparisonCount,
            "eager_property_initialization_count" to metrics.eagerPropertyInitializationCount,
            "findings" to
                metrics.findings.map { finding ->
                    linkedMapOf(
                        "code" to finding.code,
                        "message" to finding.message,
                        "path" to finding.path,
                        "count" to finding.count,
                    )
                },
        )
}
