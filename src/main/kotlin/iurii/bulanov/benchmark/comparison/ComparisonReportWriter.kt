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
        appendRow("Missing outputs", kinds.map { listSizeCell(it, "file_coverage", "missing_kotlin_files") })
        appendRow("Control-flow fidelity", kinds.map { score(it.number("content", "control_flow_fidelity_score")) })
        appendRow("Nullability inference accuracy", kinds.map { nullabilityAccuracyCell(it) })
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
        markdownRows(section).forEach { row ->
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
            MetricType.SCORE -> score(kind.number(row.section, row.key))
            MetricType.TEXT -> kind.text(row.section, row.key) ?: "—"
            MetricType.LIST_SIZE -> listSizeCell(kind, row)
            MetricType.CONTENT_SHAPE -> contentShapeCell(kind)
            MetricType.COUNT_PRESERVATION -> countPreservationCell(kind, row.key)
            MetricType.RATE -> rateCell(kind, row.key)
            MetricType.RETURN_DENSITY -> returnDensityCell(kind)
            MetricType.BRANCH_COMPLEXITY -> branchComplexityCell(kind)
            MetricType.NULLABILITY_ACCURACY -> nullabilityAccuracyCell(kind)
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
            MetricType.SCORE -> kind.number(row.section, row.key)
            MetricType.TEXT -> kind.text(row.section, row.key)
            MetricType.LIST_SIZE -> kind.listSize(row.section, row.key)
            MetricType.CONTENT_SHAPE -> kind.int(row.section, row.key)
            MetricType.COUNT_PRESERVATION -> kind.number(row.section, row.key)
            MetricType.RATE -> kind.number(row.section, row.key)
            MetricType.RETURN_DENSITY -> kind.number(row.section, row.key)
            MetricType.BRANCH_COMPLEXITY -> kind.number(row.section, row.key)
            MetricType.NULLABILITY_ACCURACY -> kind.number(row.section, row.key)
        }

    /** Renders list sizes while preserving absent fields as unknown. */
    private fun listSizeCell(
        kind: KindEvaluation,
        row: MetricRow,
    ): String = listSizeCell(kind, row.section, row.key)

    private fun listSizeCell(
        kind: KindEvaluation,
        section: String,
        key: String,
    ): String =
        if (kind.hasMetric(section, key)) {
            kind.listSize(section, key).toString()
        } else {
            "—"
        }

    /** Returns the Markdown rows for [section], with count-first content rows. */
    private fun markdownRows(section: ReportSection): List<MetricRow> =
        if (section.title == CONTENT_PRESERVATION_SECTION_TITLE) {
            CONTENT_MARKDOWN_ROWS
        } else {
            section.rows
        }

    /** Renders the content-shape preservation cell from existing content counts and score. */
    private fun contentShapeCell(kind: KindEvaluation): String {
        val preserved = kind.int("content", "content_shape_preserved_file_count") ?: return "—"
        val matched = kind.int("content", "matched_file_count") ?: return "—"
        val score = kind.number("content", "content_shape_preservation_rate") ?: return "—"
        return countPreservation(preserved, matched, score)
    }

    /** Renders a control-flow count preservation cell selected by [scoreKey]. */
    private fun countPreservationCell(
        kind: KindEvaluation,
        scoreKey: String,
    ): String {
        val keys =
            when (scoreKey) {
                "return_preservation_ratio" -> CountPreservationKeys("kotlin_return_count", "java_return_count")
                "branch_preservation_ratio" -> CountPreservationKeys("kotlin_branch_count", "java_branch_count")
                "throw_preservation_ratio" -> CountPreservationKeys("kotlin_throw_count", "java_throw_count")
                "try_preservation_ratio" -> CountPreservationKeys("kotlin_try_count", "java_try_count")
                else -> return "—"
            }
        val kotlinCount = kind.int("content", keys.kotlinKey) ?: return "—"
        val javaCount = kind.int("content", keys.javaKey) ?: return "—"
        val score = kind.number("content", scoreKey) ?: return "—"
        return countPreservation(kotlinCount, javaCount, score)
    }

    /** Renders a Java or Kotlin rate from the underlying count and function declaration fields. */
    private fun rateCell(
        kind: KindEvaluation,
        key: String,
    ): String {
        val (numeratorKey, denominatorKey) =
            when (key) {
                "java_return_density" -> "java_return_count" to "java_function_declaration_count"
                "kotlin_return_density" -> "kotlin_return_count" to "kotlin_function_declaration_count"
                "java_control_flow_rate" -> "java_control_flow_count" to "java_function_declaration_count"
                "kotlin_control_flow_rate" -> "kotlin_control_flow_count" to "kotlin_function_declaration_count"
                else -> return "—"
            }
        val numerator = countForRate(kind, numeratorKey) ?: return "—"
        val denominator = kind.int("content", denominatorKey) ?: return "—"
        return rate(numerator, denominator)
    }

    private fun countForRate(
        kind: KindEvaluation,
        key: String,
    ): Int? =
        when (key) {
            "java_control_flow_count" ->
                kind
                    .int("content", "java_branch_count")
                    ?.plus(kind.int("content", "java_loop_count") ?: return null)
                    ?.plus(kind.int("content", "java_try_count") ?: return null)
            "kotlin_control_flow_count" ->
                kind
                    .int("content", "kotlin_branch_count")
                    ?.plus(kind.int("content", "kotlin_loop_count") ?: return null)
                    ?.plus(kind.int("content", "kotlin_try_count") ?: return null)
            else -> kind.int("content", key)
        }

    /** Renders return-statement density from return and function counts. */
    private fun returnDensityCell(kind: KindEvaluation): String {
        val javaReturns = kind.int("content", "java_return_count") ?: return "—"
        val kotlinReturns = kind.int("content", "kotlin_return_count") ?: return "—"
        val javaFunctions = kind.int("content", "java_function_declaration_count") ?: return "—"
        val kotlinFunctions = kind.int("content", "kotlin_function_declaration_count") ?: return "—"
        val score = kind.number("content", "return_statement_density_preservation") ?: return "—"
        return ratePreservation(javaReturns, javaFunctions, kotlinReturns, kotlinFunctions, score)
    }

    /** Renders branch complexity from branch, loop, try, and function counts. */
    private fun branchComplexityCell(kind: KindEvaluation): String {
        val javaBranches = kind.int("content", "java_branch_count") ?: return "—"
        val javaLoops = kind.int("content", "java_loop_count") ?: return "—"
        val javaTries = kind.int("content", "java_try_count") ?: return "—"
        val javaFunctions = kind.int("content", "java_function_declaration_count") ?: return "—"
        val kotlinBranches = kind.int("content", "kotlin_branch_count") ?: return "—"
        val kotlinLoops = kind.int("content", "kotlin_loop_count") ?: return "—"
        val kotlinTries = kind.int("content", "kotlin_try_count") ?: return "—"
        val kotlinFunctions = kind.int("content", "kotlin_function_declaration_count") ?: return "—"
        val score = kind.number("content", "branch_complexity_index_preservation") ?: return "—"
        val javaComplexityCount = javaBranches + javaLoops + javaTries
        val kotlinComplexityCount = kotlinBranches + kotlinLoops + kotlinTries
        return ratePreservation(javaComplexityCount, javaFunctions, kotlinComplexityCount, kotlinFunctions, score)
    }

    /** Renders nullability inference accuracy as non-contradictory operations over all operations. */
    private fun nullabilityAccuracyCell(kind: KindEvaluation): String {
        val total = kind.int("nullability", "total_nullability_operation_count") ?: return "—"
        val contradictory = kind.int("nullability", "contradictory_nullability_patterns") ?: return "—"
        val score = kind.number("nullability", "nullability_inference_accuracy") ?: return "—"
        val nonContradictory = (total - contradictory).coerceAtLeast(0)
        return "$nonContradictory/$total (${scorePercent(score)})"
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

    /** Formats a score-style ratio with a stable decimal separator, or `—` when absent. */
    private fun score(value: Double?): String = value?.let { String.format(Locale.US, "%.3f", it) } ?: "—"

    private fun scorePercent(value: Double): String = String.format(Locale.US, "%.1f%%", value * PERCENT_SCALE)

    private fun countPreservation(
        numerator: Int,
        denominator: Int,
        score: Double,
    ): String = "$numerator/$denominator (${scorePercent(score)}${cappedSuffix(numerator, denominator, score)})"

    private fun ratePreservation(
        javaNumerator: Int,
        javaDenominator: Int,
        kotlinNumerator: Int,
        kotlinDenominator: Int,
        score: Double,
    ): String {
        val javaRate = density(javaNumerator, javaDenominator)
        val kotlinRate = density(kotlinNumerator, kotlinDenominator)
        return "${formatRate(kotlinRate)}/${formatRate(javaRate)} " +
            "(${scorePercent(score)}${cappedDensitySuffix(javaRate, kotlinRate, score)})"
    }

    private fun cappedSuffix(
        numerator: Int,
        denominator: Int,
        score: Double,
    ): String =
        if (score >= PERFECT_PRESERVATION && numeratorExceedsDenominator(numerator, denominator)) {
            ", capped"
        } else {
            ""
        }

    private fun cappedDensitySuffix(
        javaDensity: Double,
        kotlinDensity: Double,
        score: Double,
    ): String =
        if (score >= PERFECT_PRESERVATION && kotlinDensityExceedsJavaDensity(kotlinDensity, javaDensity)) {
            ", capped"
        } else {
            ""
        }

    private fun numeratorExceedsDenominator(
        numerator: Int,
        denominator: Int,
    ): Boolean =
        if (denominator == 0) {
            numerator > 0
        } else {
            numerator.toDouble() / denominator.toDouble() > PERFECT_PRESERVATION
        }

    private fun kotlinDensityExceedsJavaDensity(
        kotlinDensity: Double,
        javaDensity: Double,
    ): Boolean =
        if (javaDensity == 0.0) {
            kotlinDensity > 0.0
        } else {
            kotlinDensity / javaDensity > PERFECT_PRESERVATION
        }

    private fun density(
        numerator: Int,
        denominator: Int,
    ): Double =
        if (denominator == 0) {
            0.0
        } else {
            numerator.toDouble() / denominator.toDouble()
        }

    private fun rate(
        numerator: Int,
        denominator: Int,
    ): String = "$numerator/$denominator = ${formatRate(density(numerator, denominator))}"

    private fun formatRate(value: Double): String = String.format(Locale.US, "%.3f", value)

    private companion object {
        /** Below this coverage a kind's quality counts are flagged as not comparable. */
        private const val LOW_COVERAGE_WARNING_PERCENT = 90.0

        private const val CONTENT_PRESERVATION_SECTION_TITLE = "Content Preservation"
        private const val PERCENT_SCALE = 100.0
        private const val PERFECT_PRESERVATION = 1.0

        private val CONTENT_MARKDOWN_ROWS =
            listOf(
                MetricRow("Matched files", "content", "matched_file_count", MetricType.INT),
                MetricRow("Java non-empty methods", "content", "java_non_empty_method_count", MetricType.INT),
                MetricRow("Kotlin non-empty functions", "content", "kotlin_non_empty_function_count", MetricType.INT),
                MetricRow("Missing Kotlin bodies", "content", "missing_kotlin_bodies", MetricType.LIST_SIZE),
                MetricRow("Content-shape mismatches", "content", "content_shape_mismatch_files", MetricType.LIST_SIZE),
                MetricRow("Java function declarations", "content", "java_function_declaration_count", MetricType.INT),
                MetricRow("Kotlin function declarations", "content", "kotlin_function_declaration_count", MetricType.INT),
                MetricRow(
                    "Content shape preserved files (preserved/matched files)",
                    "content",
                    "content_shape_preserved_file_count",
                    MetricType.CONTENT_SHAPE,
                ),
                MetricRow("Content-shape mismatch file count", "content", "content_shape_mismatch_file_count", MetricType.INT),
                MetricRow(
                    "Returns preserved (Kotlin/Java returns)",
                    "content",
                    "return_preservation_ratio",
                    MetricType.COUNT_PRESERVATION,
                ),
                MetricRow(
                    "Branches preserved (Kotlin/Java branches)",
                    "content",
                    "branch_preservation_ratio",
                    MetricType.COUNT_PRESERVATION,
                ),
                MetricRow(
                    "Throws preserved (Kotlin/Java throws)",
                    "content",
                    "throw_preservation_ratio",
                    MetricType.COUNT_PRESERVATION,
                ),
                MetricRow(
                    "Try blocks preserved (Kotlin/Java try blocks)",
                    "content",
                    "try_preservation_ratio",
                    MetricType.COUNT_PRESERVATION,
                ),
                MetricRow("Control-flow fidelity score", "content", "control_flow_fidelity_score", MetricType.SCORE),
                MetricRow("Java return rate (Java returns/functions)", "content", "java_return_density", MetricType.RATE),
                MetricRow("Kotlin return rate (Kotlin returns/functions)", "content", "kotlin_return_density", MetricType.RATE),
                MetricRow(
                    "Return rate preserved (Kotlin/Java return rate)",
                    "content",
                    "return_statement_density_preservation",
                    MetricType.RETURN_DENSITY,
                ),
                MetricRow(
                    "Java control-flow rate ((branches + loops + tries)/functions)",
                    "content",
                    "java_control_flow_rate",
                    MetricType.RATE,
                ),
                MetricRow(
                    "Kotlin control-flow rate ((branches + loops + tries)/functions)",
                    "content",
                    "kotlin_control_flow_rate",
                    MetricType.RATE,
                ),
                MetricRow(
                    "Control-flow rate preserved (Kotlin/Java control-flow rate)",
                    "content",
                    "branch_complexity_index_preservation",
                    MetricType.BRANCH_COMPLEXITY,
                ),
            )

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
                        MetricRow("Java function declarations", "content", "java_function_declaration_count", MetricType.INT),
                        MetricRow("Kotlin function declarations", "content", "kotlin_function_declaration_count", MetricType.INT),
                        MetricRow("Content-shape preserved files", "content", "content_shape_preserved_file_count", MetricType.INT),
                        MetricRow("Content-shape mismatch file count", "content", "content_shape_mismatch_file_count", MetricType.INT),
                        MetricRow("Return preservation ratio", "content", "return_preservation_ratio", MetricType.SCORE),
                        MetricRow("Branch preservation ratio", "content", "branch_preservation_ratio", MetricType.SCORE),
                        MetricRow("Throw preservation ratio", "content", "throw_preservation_ratio", MetricType.SCORE),
                        MetricRow("Try preservation ratio", "content", "try_preservation_ratio", MetricType.SCORE),
                        MetricRow("Control-flow fidelity score", "content", "control_flow_fidelity_score", MetricType.SCORE),
                        MetricRow("Content-shape preservation rate", "content", "content_shape_preservation_rate", MetricType.SCORE),
                        MetricRow("Java return density", "content", "java_return_density", MetricType.SCORE),
                        MetricRow("Kotlin return density", "content", "kotlin_return_density", MetricType.SCORE),
                        MetricRow(
                            "Return density preservation",
                            "content",
                            "return_statement_density_preservation",
                            MetricType.SCORE,
                        ),
                        MetricRow("Java branch complexity index", "content", "java_branch_complexity_index", MetricType.SCORE),
                        MetricRow("Kotlin branch complexity index", "content", "kotlin_branch_complexity_index", MetricType.SCORE),
                        MetricRow(
                            "Branch complexity preservation",
                            "content",
                            "branch_complexity_index_preservation",
                            MetricType.SCORE,
                        ),
                    ),
                ),
                ReportSection(
                    "Nullability Signals",
                    listOf(
                        MetricRow("Java nullable annotations", "nullability", "java_nullable_annotation_count", MetricType.INT),
                        MetricRow("Java not-null annotations", "nullability", "java_not_null_annotation_count", MetricType.INT),
                        MetricRow("Kotlin nullable types", "nullability", "kotlin_nullable_type_count", MetricType.INT),
                        MetricRow(
                            "Contradictory nullability patterns",
                            "nullability",
                            "contradictory_nullability_patterns",
                            MetricType.INT,
                        ),
                        MetricRow("Null comparisons", "nullability", "null_comparison_count", MetricType.INT),
                        MetricRow("Nullability casts", "nullability", "nullability_cast_count", MetricType.INT),
                        MetricRow("Safe calls", "nullability", "safe_call_count", MetricType.INT),
                        MetricRow("Total nullability operations", "nullability", "total_nullability_operation_count", MetricType.INT),
                        MetricRow(
                            "Nullability inference accuracy (non-contradictory/total operations)",
                            "nullability",
                            "nullability_inference_accuracy",
                            MetricType.NULLABILITY_ACCURACY,
                        ),
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

/** JSON keys and labels for a generated/source count preservation row. */
private data class CountPreservationKeys(
    val kotlinKey: String,
    val javaKey: String,
)

/** How a metric value is read and rendered. */
private enum class MetricType {
    INT,
    PERCENT,
    SCORE,
    TEXT,
    LIST_SIZE,
    CONTENT_SHAPE,
    COUNT_PRESERVATION,
    RATE,
    RETURN_DENSITY,
    BRANCH_COMPLEXITY,
    NULLABILITY_ACCURACY,
}
