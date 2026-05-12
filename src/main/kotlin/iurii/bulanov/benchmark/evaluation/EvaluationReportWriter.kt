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
                "benchmark" to benchmarkJson(result),
                "paths" to pathsJson(result),
                "checkout" to checkoutJson(result.checkout),
                "conversion" to conversionJson(result.conversion),
                "file_coverage" to fileCoverageJson(result.fileCoverage),
                "structure" to structureJson(result.structure),
                "quality" to qualityJson(result.quality),
                "analysis" to assignmentAnalysisJson(result),
                "counts" to countsJson(result),
                "status" to result.status.name.lowercase(),
                "warnings" to warningsJson(result.warnings),
            ),
        ) + "\n"

    /**
     * Renders the human-readable evaluator summary report body.
     */
    fun renderMarkdown(result: EvaluationResult): String =
        buildString {
            appendLine("# J2K Evaluation Summary")
            appendResultInterpretation(result)
            appendPaths(result)
            appendConversion(result.conversion)
            appendCheckout(result.checkout)
            appendFileCoverage(result.fileCoverage)
            appendStructure(result.structure)
            appendQuality(result.quality)
            appendNotableFailures(result)
            appendWarnings(result)
        }

    /**
     * Appends a compact human verdict for the benchmark run.
     */
    private fun StringBuilder.appendResultInterpretation(result: EvaluationResult) {
        appendLine()
        appendLine("## Result Interpretation")
        appendLine()
        appendLine("- Verdict: ${resultVerdict(result)}")
        appendLine(
            "- Coverage: `${result.fileCoverage.matchedKotlinFileCount}` of `${result.fileCoverage.javaFileCount}` " +
                "configured Java inputs have matching generated Kotlin files " +
                "(`${formatPercent(result.fileCoverage.coveragePercent)}%`).",
        )
        appendLine(
            "- Structural comparison: Java declarations/functions are compared with generated Kotlin " +
                "declarations/functions as deterministic heuristics, not as a compiler proof.",
        )
        appendLine(
            "- Quality review: `${result.quality.findings.size}` Kotlin quality warnings were produced for " +
                "manual review.",
        )
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
        appendLine(
            "Generated Kotlin is compared with " +
                "the original Java source using deterministic structural heuristics.",
        )
        appendLine()
        appendLine("- Java declarations: `${structure.javaTopLevelDeclarationCount}`")
        appendLine("- Kotlin declarations: `${structure.kotlinTopLevelDeclarationCount}`")
        appendLine("- Java class-like declarations: `${structure.javaClassLikeCount}`")
        appendLine("- Kotlin class-like declarations: `${structure.kotlinClassLikeCount}`")
        appendLine("- Java interfaces: `${structure.javaInterfaceCount}`")
        appendLine("- Kotlin interfaces: `${structure.kotlinInterfaceCount}`")
        appendLine("- Java enums: `${structure.javaEnumCount}`")
        appendLine("- Kotlin enums: `${structure.kotlinEnumCount}`")
        appendLine("- Java methods: `${structure.javaMethodCount}`")
        appendLine("- Kotlin functions: `${structure.kotlinFunctionCount}`")
        appendLine("- Public API name overlap: `${structure.publicApiNameOverlapCount}`")
        appendLine("- Java API names missing in Kotlin: `${structure.missingPublicApiNames.size}`")
        appendLine("- Kotlin-only API names: `${structure.kotlinOnlyPublicApiNames.size}`")
        appendLine()
        appendLine("### Structural Name Differences")
        appendLine()
        appendLine("These name lists are heuristic; Java getters may become Kotlin properties.")
        appendNameDiff(
            "Classes and records",
            "Java classes/records missing in Kotlin",
            "Kotlin classes not present in Java",
            structure.nameDiffs.classLike,
        )
        appendNameDiff(
            "Interfaces",
            "Java interfaces missing in Kotlin",
            "Kotlin interfaces not present in Java",
            structure.nameDiffs.interfaces,
        )
        appendNameDiff("Enums", "Java enums missing in Kotlin", "Kotlin enums not present in Java", structure.nameDiffs.enums)
        appendNameList("Kotlin objects not present in Java", structure.nameDiffs.objects.kotlinOnly)
        appendNameDiff(
            "Methods and functions",
            "Java methods missing as Kotlin functions",
            "Kotlin functions not present as Java methods",
            structure.nameDiffs.functions,
        )
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
     * Appends concrete conversion failures and review risks.
     */
    private fun StringBuilder.appendNotableFailures(result: EvaluationResult) {
        appendLine()
        appendLine("## Notable Failures")
        appendLine()
        val hasFailures =
            result.conversion.errors.isNotEmpty() ||
                result.fileCoverage.missingKotlinFiles.isNotEmpty() ||
                result.fileCoverage.packageMismatchFiles.isNotEmpty() ||
                result.quality.findings.isNotEmpty()

        if (!hasFailures) {
            appendLine("- None")
            return
        }

        if (result.conversion.errors.isNotEmpty()) {
            appendLine("- Converter errors: `${result.conversion.errors.size}`")
            result.conversion.errors.forEach { error -> appendLine("  - `$error`") }
        }
        if (result.fileCoverage.missingKotlinFiles.isNotEmpty()) {
            appendLine("- Missing generated Kotlin files: `${result.fileCoverage.missingKotlinFiles.size}`")
            result.fileCoverage.missingKotlinFiles.forEach { missingFile -> appendLine("  - `$missingFile`") }
        }
        if (result.fileCoverage.packageMismatchFiles.isNotEmpty()) {
            appendLine("- Package mismatches: `${result.fileCoverage.packageMismatchFiles.size}`")
            result.fileCoverage.packageMismatchFiles.forEach { mismatch -> appendLine("  - `$mismatch`") }
        }
        if (result.quality.findings.isNotEmpty()) {
            appendLine(
                "- Kotlin quality review warnings: `${result.quality.findings.size}` " +
                    "(review risks, not conversion execution failures)",
            )
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
     * Appends a capped deterministic list of structural API names.
     */
    private fun StringBuilder.appendNameList(
        title: String,
        names: List<String>,
    ) {
        appendLine()
        if (names.isEmpty()) {
            appendLine("- $title: none")
            return
        }

        val displayedNames = names.take(STRUCTURAL_NAME_DISPLAY_LIMIT)
        val suffix =
            if (names.size > STRUCTURAL_NAME_DISPLAY_LIMIT) {
                "first `${displayedNames.size}` of `${names.size}`"
            } else {
                "all `${names.size}`"
            }
        appendLine("- $title ($suffix):")
        displayedNames.forEach { name -> appendLine("  - `$name`") }
    }

    /**
     * Appends one grouped two-way structural name diff.
     */
    private fun StringBuilder.appendNameDiff(
        title: String,
        missingInKotlinTitle: String,
        kotlinOnlyTitle: String,
        diff: StructuralNameDiff,
    ) {
        appendLine()
        appendLine("#### $title")
        appendNameList(missingInKotlinTitle, diff.missingInKotlin)
        appendNameList(kotlinOnlyTitle, diff.kotlinOnly)
    }

    /**
     * Formats report percentages with stable decimal separators across locales.
     */
    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f", value)

    /**
     * Renders benchmark metadata to a JSON-compatible map.
     */
    private fun benchmarkJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "id" to result.config.id,
            "name" to result.config.name,
            "role" to result.config.role,
            "repository_source" to result.config.repository.source,
            "repository_upstream" to result.config.repository.upstream,
            "repository_ref" to result.config.repository.ref,
        )

    /**
     * Renders evaluator paths to a JSON-compatible map.
     */
    private fun pathsJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "checkout_directory" to result.checkoutDirectory.toString(),
            "generated_kotlin_directory" to result.generatedKotlinDirectory.toString(),
            "conversion_report" to result.conversionReportPath.toString(),
            "checkout_report" to result.checkoutReportPath.toString(),
            "report_directory" to result.reportDirectory.toString(),
        )

    /**
     * Renders checkout metadata to a JSON-compatible map.
     */
    private fun checkoutJson(checkout: CheckoutEvaluation): Map<String, Any?> =
        linkedMapOf(
            "available" to checkout.available,
            "benchmark_id" to checkout.benchmarkId,
            "build_status" to checkout.buildStatus,
            "java_file_count" to checkout.javaFileCount,
            "run_build" to checkout.runBuild,
        )

    /**
     * Renders conversion metadata to a JSON-compatible map.
     */
    private fun conversionJson(conversion: ConversionEvaluation): Map<String, Any?> =
        linkedMapOf(
            "available" to conversion.available,
            "benchmark_id" to conversion.benchmarkId,
            "status" to conversion.status,
            "source_java_file_count" to conversion.sourceJavaFileCount,
            "generated_kotlin_file_count" to conversion.generatedKotlinFileCount,
            "warning_count" to conversion.warningCount,
            "error_count" to conversion.errorCount,
            "warnings" to conversion.warnings,
            "errors" to conversion.errors,
        )

    /**
     * Renders assignment-facing analysis metadata to a JSON-compatible map.
     */
    private fun assignmentAnalysisJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "benchmark_role" to result.config.role,
            "analysis_method" to "structural_heuristics",
            "conversion_status" to result.conversion.status,
            "evaluation_status" to result.status.name.lowercase(),
            "file_coverage_percent" to result.fileCoverage.coveragePercent,
            "missing_output_count" to result.fileCoverage.missingKotlinFiles.size,
            "quality_warning_count" to result.quality.findings.size,
        )

    /**
     * Renders summary counts to a JSON-compatible map.
     */
    private fun countsJson(result: EvaluationResult): Map<String, Any?> =
        linkedMapOf(
            "java_file_count" to result.fileCoverage.javaFileCount,
            "kotlin_file_count" to result.fileCoverage.kotlinFileCount,
            "warning_count" to result.warnings.size,
        )

    /**
     * Renders evaluator warnings to JSON-compatible maps.
     */
    private fun warningsJson(warnings: List<EvaluationWarning>): List<Map<String, Any?>> =
        warnings.map { warning ->
            linkedMapOf(
                "code" to warning.code,
                "message" to warning.message,
                "path" to warning.path,
                "count" to warning.count,
            )
        }

    /**
     * Builds a concise benchmark verdict from conversion and coverage data.
     */
    private fun resultVerdict(result: EvaluationResult): String =
        when {
            !result.conversion.available ->
                "The evaluator ran, but conversion metadata is missing, so this artifact is incomplete."
            result.conversion.errors.isNotEmpty() || result.fileCoverage.missingKotlinFiles.isNotEmpty() ->
                "Static J2K produced a partial conversion with concrete failures that need manual review."
            result.fileCoverage.coveragePercent == COMPLETE_COVERAGE ->
                "Static J2K generated Kotlin for every configured Java input; remaining warnings are quality-review signals."
            else ->
                "Static J2K produced generated Kotlin, but file coverage is incomplete and should be reviewed."
        }

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
            "kotlin_only_public_api_names" to metrics.kotlinOnlyPublicApiNames,
            "name_diffs" to nameDiffsJson(metrics.nameDiffs),
        )

    /**
     * Renders grouped structural name diffs to a JSON-compatible map.
     */
    private fun nameDiffsJson(nameDiffs: StructuralNameDiffs): Map<String, Any?> =
        linkedMapOf(
            "class_like" to nameDiffJson(nameDiffs.classLike),
            "interfaces" to nameDiffJson(nameDiffs.interfaces),
            "enums" to nameDiffJson(nameDiffs.enums),
            "objects" to nameDiffJson(nameDiffs.objects),
            "functions" to nameDiffJson(nameDiffs.functions),
        )

    /**
     * Renders one structural name diff to a JSON-compatible map.
     */
    private fun nameDiffJson(diff: StructuralNameDiff): Map<String, Any?> =
        linkedMapOf(
            "missing_in_kotlin" to diff.missingInKotlin,
            "kotlin_only" to diff.kotlinOnly,
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

    private companion object {
        private const val COMPLETE_COVERAGE = 100.0
        private const val STRUCTURAL_NAME_DISPLAY_LIMIT = 50
    }
}
