package iurii.bulanov.benchmark.comparison

import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.util.Locale
import kotlin.io.path.writeText

/**
 * Writes the compiled multi-kind comparison to deterministic JSON and Markdown.
 *
 * The Markdown mirrors a single run's `summary.md` section-for-section, but each section is a table
 * with one column per converter kind (the present kinds), so the four configurations can be read
 * side by side. Long per-kind lists are reduced to counts here; the `Divergences` section surfaces
 * the set differences that actually matter (e.g. files one kind converts but another fails).
 */
class ComparisonReportWriter(
    private val logger: StructuredLogger = JsonLineLogger(),
) {
    /**
     * Writes `comparison.json` and `comparison.md` under [reportDirectory].
     */
    fun write(
        comparison: Comparison,
        reportDirectory: java.nio.file.Path,
    ) {
        Files.createDirectories(reportDirectory)
        val jsonPath = reportDirectory.resolve("comparison.json")
        val summaryPath = reportDirectory.resolve("comparison.md")
        jsonPath.writeText(renderJson(comparison))
        summaryPath.writeText(renderMarkdown(comparison))
        logger.info(
            "comparison_reports_written",
            mapOf("json_path" to jsonPath.toString(), "summary_path" to summaryPath.toString()),
        )
    }

    /**
     * Renders the human-readable comparison: a summary table, the per-kind metric tables, and a
     * divergences section.
     */
    fun renderMarkdown(comparison: Comparison): String =
        buildString {
            val kinds = comparison.kinds
            appendLine("# J2K Converter Comparison — ${comparison.benchmarkName} (`${comparison.benchmarkId}`)")
            appendLine()
            appendLine("Kinds compared: ${kinds.joinToString(", ") { "`${it.kind.id}`" }}")
            if (comparison.missingKinds.isNotEmpty()) {
                appendLine()
                appendLine("> Missing (no evaluation report): ${comparison.missingKinds.joinToString(", ") { "`${it.id}`" }}")
            }
            if (kinds.isEmpty()) {
                appendLine()
                appendLine("No kind reports were available to compare.")
                return@buildString
            }
            appendSummary(kinds)
            SECTIONS.forEach { section -> appendSection(section, kinds) }
            appendDivergences(comparison)
        }

    /**
     * Renders the machine-readable comparison: per-section metric maps keyed by kind, plus
     * divergences.
     */
    fun renderJson(comparison: Comparison): String =
        JsonEncoder.encode(
            linkedMapOf(
                "benchmark" to linkedMapOf("id" to comparison.benchmarkId, "name" to comparison.benchmarkName),
                "kinds" to comparison.kinds.map { it.kind.id },
                "missing_kinds" to comparison.missingKinds.map { it.id },
                "low_coverage_kinds" to lowCoverageKinds(comparison.kinds).map { it.kind.id },
                "sections" to sectionsJson(comparison.kinds),
                "divergences" to divergencesJson(comparison),
            ),
        ) + "\n"

    /**
     * Appends a compact at-a-glance table (status + coverage + headline quality) per kind.
     */
    private fun StringBuilder.appendSummary(kinds: List<KindEvaluation>) {
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendRow("Metric", kinds.map { "`${it.kind.id}`" })
        appendSeparator(kinds.size)
        appendRow("Conversion status", kinds.map { it.text("conversion", "status") ?: "—" })
        appendRow("Coverage %", kinds.map { percent(it.number("file_coverage", "coverage_percent")) })
        appendRow("Matched / Java", kinds.map { matchedOverJava(it) })
        appendRow("Missing outputs", kinds.map { it.listSize("file_coverage", "missing_kotlin_files").toString() })
        appendRow("`!!` assertions", kinds.map { (it.int("quality", "not_null_assertion_count") ?: "—").toString() })
        appendRow("Evaluator warnings", kinds.map { it.warningCount.toString() })
        appendLowCoverageWarnings(kinds)
    }

    /**
     * Flags kinds whose conversion coverage is too low for their quality counts to be comparable
     * (e.g. "0 `!!`" means nothing when only 1 of 49 files converted).
     */
    private fun StringBuilder.appendLowCoverageWarnings(kinds: List<KindEvaluation>) {
        lowCoverageKinds(kinds).forEach { kind ->
            appendLine()
            appendLine(
                "> ⚠ `${kind.kind.id}` converted only ${matchedOverJava(kind)} files " +
                    "(${percent(kind.number("file_coverage", "coverage_percent"))}) — " +
                    "its quality counts are not comparable with the other kinds.",
            )
        }
    }

    /**
     * Returns the kinds below the coverage threshold for comparable quality metrics.
     */
    private fun lowCoverageKinds(kinds: List<KindEvaluation>): List<KindEvaluation> =
        kinds.filter { (it.number("file_coverage", "coverage_percent") ?: 0.0) < LOW_COVERAGE_WARNING_PERCENT }

    /**
     * Appends one mirrored section as a per-kind table.
     */
    private fun StringBuilder.appendSection(
        section: ReportSection,
        kinds: List<KindEvaluation>,
    ) {
        appendLine()
        appendLine("## ${section.title}")
        appendLine()
        appendRow("Metric", kinds.map { "`${it.kind.id}`" })
        appendSeparator(kinds.size)
        section.rows.forEach { row ->
            appendRow(row.label, kinds.map { cell(it, row) })
        }
    }

    /**
     * Appends files that converted in some kinds but not others (the most actionable divergence).
     */
    private fun StringBuilder.appendDivergences(comparison: Comparison) {
        appendLine()
        appendLine("## Divergences")
        appendLine()
        val missingByFile = missingFileToKinds(comparison.kinds)
        if (missingByFile.isEmpty()) {
            appendLine("- No per-file coverage divergences across the compared kinds.")
            return
        }
        appendLine("Generated Kotlin files missing for some kinds but produced by others:")
        appendLine()
        missingByFile.toSortedMap().forEach { (file, missingKinds) ->
            appendLine("- `$file` — missing in ${missingKinds.joinToString(", ") { "`$it`" }}")
        }
    }

    /**
     * Maps each missing generated file to the kinds that failed to produce it (divergent files only).
     */
    private fun missingFileToKinds(kinds: List<KindEvaluation>): Map<String, List<String>> {
        val byFile = linkedMapOf<String, MutableList<String>>()
        kinds.forEach { kind ->
            kind.missingKotlinFiles.forEach { file ->
                byFile.getOrPut(file) { mutableListOf() }.add(kind.kind.id)
            }
        }
        // A divergence means missing for some kinds but not all compared kinds.
        return byFile.filterValues { it.size < kinds.size }
    }

    /**
     * Renders one metric cell for a kind.
     */
    private fun cell(
        kind: KindEvaluation,
        row: MetricRow,
    ): String =
        when (row.type) {
            MetricType.INT -> (kind.int(row.section, row.key) ?: "—").toString()
            MetricType.PERCENT -> percent(kind.number(row.section, row.key))
            MetricType.TEXT -> kind.text(row.section, row.key) ?: "—"
            MetricType.LIST_SIZE -> kind.listSize(row.section, row.key).toString()
        }

    /**
     * Renders per-section metric values keyed by kind for the JSON report.
     */
    private fun sectionsJson(kinds: List<KindEvaluation>): Map<String, Any?> =
        SECTIONS.associate { section ->
            section.title to
                section.rows.associate { row ->
                    row.key to kinds.associate { it.kind.id to rawValue(it, row) }
                }
        }

    /**
     * Renders the divergence map for the JSON report.
     */
    private fun divergencesJson(comparison: Comparison): Map<String, Any?> =
        linkedMapOf("files_missing_in_some_kinds" to missingFileToKinds(comparison.kinds))

    /**
     * Returns the raw (unformatted) metric value for the JSON report.
     */
    private fun rawValue(
        kind: KindEvaluation,
        row: MetricRow,
    ): Any? =
        when (row.type) {
            MetricType.INT -> kind.int(row.section, row.key)
            MetricType.PERCENT -> kind.number(row.section, row.key)
            MetricType.TEXT -> kind.text(row.section, row.key)
            MetricType.LIST_SIZE -> kind.listSize(row.section, row.key)
        }

    /** Formats "matched / total Java files" for a kind. */
    private fun matchedOverJava(kind: KindEvaluation): String {
        val matched = kind.int("file_coverage", "matched_kotlin_file_count") ?: "—"
        val java = kind.int("file_coverage", "java_file_count") ?: "—"
        return "$matched/$java"
    }

    /** Appends a Markdown table row. */
    private fun StringBuilder.appendRow(
        label: String,
        cells: List<String>,
    ) {
        appendLine("| $label | ${cells.joinToString(" | ")} |")
    }

    /** Appends a Markdown table header separator for [columns] kind columns. */
    private fun StringBuilder.appendSeparator(columns: Int) {
        appendLine("| --- | ${List(columns) { "---" }.joinToString(" | ")} |")
    }

    /** Formats a percentage with a stable decimal separator, or `—` when absent. */
    private fun percent(value: Double?): String = value?.let { String.format(Locale.US, "%.2f%%", it) } ?: "—"

    private companion object {
        /** Below this coverage a kind's quality counts are flagged as not comparable. */
        private const val LOW_COVERAGE_WARNING_PERCENT = 90.0

        private val SECTIONS =
            listOf(
                ReportSection(
                    "Conversion Execution",
                    listOf(
                        MetricRow("Status", "conversion", "status", MetricType.TEXT),
                        MetricRow("Source Java files", "conversion", "source_java_file_count", MetricType.INT),
                        MetricRow("Generated Kotlin files", "conversion", "generated_kotlin_file_count", MetricType.INT),
                        MetricRow("Converter warnings", "conversion", "warning_count", MetricType.INT),
                        MetricRow("Converter errors", "conversion", "error_count", MetricType.INT),
                    ),
                ),
                ReportSection(
                    "File Coverage",
                    listOf(
                        MetricRow("Coverage %", "file_coverage", "coverage_percent", MetricType.PERCENT),
                        MetricRow("Java files", "file_coverage", "java_file_count", MetricType.INT),
                        MetricRow("Kotlin files", "file_coverage", "kotlin_file_count", MetricType.INT),
                        MetricRow("Matched Kotlin files", "file_coverage", "matched_kotlin_file_count", MetricType.INT),
                        MetricRow("Missing outputs", "file_coverage", "missing_kotlin_files", MetricType.LIST_SIZE),
                        MetricRow("Unexpected outputs", "file_coverage", "unexpected_kotlin_files", MetricType.LIST_SIZE),
                        MetricRow("Empty generated files", "file_coverage", "empty_generated_files", MetricType.LIST_SIZE),
                        MetricRow("Package preservation %", "file_coverage", "package_preservation_percent", MetricType.PERCENT),
                    ),
                ),
                ReportSection(
                    "Structural Preservation",
                    listOf(
                        MetricRow("Java declarations", "structure", "java_top_level_declaration_count", MetricType.INT),
                        MetricRow("Kotlin declarations", "structure", "kotlin_top_level_declaration_count", MetricType.INT),
                        MetricRow("Java class-like", "structure", "java_class_like_count", MetricType.INT),
                        MetricRow("Kotlin class-like", "structure", "kotlin_class_like_count", MetricType.INT),
                        MetricRow("Java interfaces", "structure", "java_interface_count", MetricType.INT),
                        MetricRow("Kotlin interfaces", "structure", "kotlin_interface_count", MetricType.INT),
                        MetricRow("Java methods", "structure", "java_method_count", MetricType.INT),
                        MetricRow("Kotlin functions", "structure", "kotlin_function_count", MetricType.INT),
                        MetricRow("Public API overlap", "structure", "public_api_name_overlap_count", MetricType.INT),
                        MetricRow("Missing API names", "structure", "missing_public_api_names", MetricType.LIST_SIZE),
                        MetricRow("Kotlin-only API names", "structure", "kotlin_only_public_api_names", MetricType.LIST_SIZE),
                    ),
                ),
                ReportSection(
                    "Content Preservation",
                    listOf(
                        MetricRow("Matched files", "content", "matched_file_count", MetricType.INT),
                        MetricRow("Java non-empty methods", "content", "java_non_empty_method_count", MetricType.INT),
                        MetricRow("Kotlin non-empty functions", "content", "kotlin_non_empty_function_count", MetricType.INT),
                        MetricRow("Missing Kotlin bodies", "content", "missing_kotlin_bodies", MetricType.LIST_SIZE),
                        MetricRow("Content-shape mismatches", "content", "content_shape_mismatch_files", MetricType.LIST_SIZE),
                    ),
                ),
                ReportSection(
                    "Nullability Signals",
                    listOf(
                        MetricRow("Java nullable annotations", "nullability", "java_nullable_annotation_count", MetricType.INT),
                        MetricRow("Java not-null annotations", "nullability", "java_not_null_annotation_count", MetricType.INT),
                        MetricRow("Kotlin nullable types", "nullability", "kotlin_nullable_type_count", MetricType.INT),
                        MetricRow("Nullable not preserved", "nullability", "nullable_annotations_not_preserved", MetricType.LIST_SIZE),
                        MetricRow("Not-null became nullable", "nullability", "not_null_annotations_became_nullable", MetricType.LIST_SIZE),
                    ),
                ),
                ReportSection(
                    "Kotlin Quality Warnings",
                    listOf(
                        MetricRow("`TODO()` calls", "quality", "todo_count", MetricType.INT),
                        MetricRow("`!!` assertions", "quality", "not_null_assertion_count", MetricType.INT),
                        MetricRow("`!!` inside calls", "quality", "not_null_assertion_in_call_count", MetricType.INT),
                        MetricRow("`Any?` types", "quality", "any_nullable_count", MetricType.INT),
                        MetricRow("Unresolved-looking imports", "quality", "unresolved_import_count", MetricType.INT),
                        MetricRow("Java interop leftovers", "quality", "java_interop_reference_count", MetricType.INT),
                        MetricRow("Getter/setter leftovers", "quality", "getter_setter_call_count", MetricType.INT),
                        MetricRow("Nullable boolean comparisons", "quality", "nullable_boolean_comparison_count", MetricType.INT),
                        MetricRow("Eager property initializers", "quality", "eager_property_initialization_count", MetricType.INT),
                    ),
                ),
            )
    }
}

/** A single comparison metric row: a label plus where to read it from each `evaluation.json`. */
private data class MetricRow(
    val label: String,
    val section: String,
    val key: String,
    val type: MetricType,
)

/** One mirrored report section and its metric rows. */
private data class ReportSection(
    val title: String,
    val rows: List<MetricRow>,
)

/** How a metric value is read and rendered. */
private enum class MetricType { INT, PERCENT, TEXT, LIST_SIZE }
