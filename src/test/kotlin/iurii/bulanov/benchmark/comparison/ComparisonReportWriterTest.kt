package iurii.bulanov.benchmark.comparison

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.logging.StructuredLogger
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComparisonReportWriterTest {
    @Test
    fun `renders per-kind columns and sections mirroring a single run`() {
        val comparison =
            Comparison(
                benchmarkId = "sample",
                benchmarkName = "Sample",
                kinds =
                    listOf(
                        kindEval(ConverterKind.K1_OLD_DUMB, status = "partial", coverage = 94.1, matched = 16, missing = listOf("a/B.kt")),
                        kindEval(ConverterKind.K2, status = "completed", coverage = 100.0, matched = 17, missing = emptyList()),
                    ),
                missingKinds = listOf(ConverterKind.K1_NEW),
            )

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)
        val json = ComparisonReportWriter(logger = NoopLogger).renderJson(comparison)

        // Columns per kind + missing-kind note.
        assertContains(md, "| Metric | `k1-old-dumb` | `k2` |")
        assertContains(md, "Missing (no evaluation report): `k1-new`")
        // Mirrors single-run sections.
        assertContains(md, "## File Coverage")
        assertContains(md, "## Structural Preservation")
        assertContains(md, "## Kotlin Quality Warnings")
        assertContains(md, "## Conversion Execution")
        // Transposed values.
        assertContains(md, "| Coverage % | 94.10% | 100.00% |")
        assertContains(md, "| Status | partial | completed |")
        // Divergence: a file missing in one kind but produced by the other.
        assertContains(md, "## Divergences")
        assertContains(md, "`a/B.kt` — missing in `k1-old-dumb`")

        assertContains(json, "\"kinds\":[\"k1-old-dumb\", \"k2\"]")
        assertContains(json, "\"missing_kinds\":[\"k1-new\"]")
        assertContains(json, "\"coverage_percent\"")
    }

    @Test
    fun `handles no available kinds gracefully`() {
        val comparison = Comparison("sample", "Sample", kinds = emptyList(), missingKinds = ConverterKind.entries)

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)

        assertContains(md, "No kind reports were available to compare.")
        assertFalse(md.contains("## File Coverage"))
    }

    @Test
    fun `notes when there are no per-file divergences`() {
        val comparison =
            Comparison(
                "sample",
                "Sample",
                kinds =
                    listOf(
                        kindEval(ConverterKind.K1_NEW, status = "completed", coverage = 100.0, matched = 17, missing = emptyList()),
                        kindEval(ConverterKind.K2, status = "completed", coverage = 100.0, matched = 17, missing = emptyList()),
                    ),
                missingKinds = emptyList(),
            )

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)

        assertTrue(md.contains("No per-file coverage divergences"))
    }

    /**
     * Builds a [KindEvaluation] from a minimal evaluation.json-shaped map.
     */
    private fun kindEval(
        kind: ConverterKind,
        status: String,
        coverage: Double,
        matched: Int,
        missing: List<String>,
    ): KindEvaluation =
        KindEvaluation(
            kind,
            mapOf(
                "status" to "completed_with_warnings",
                "counts" to mapOf("warning_count" to 3),
                "conversion" to mapOf("status" to status, "errors" to emptyList<String>()),
                "file_coverage" to
                    mapOf(
                        "coverage_percent" to coverage,
                        "java_file_count" to 17,
                        "matched_kotlin_file_count" to matched,
                        "missing_kotlin_files" to missing,
                    ),
                "structure" to mapOf("java_method_count" to 56, "kotlin_function_count" to 48),
                "content" to mapOf("matched_file_count" to matched),
                "nullability" to mapOf("kotlin_nullable_type_count" to 9),
                "quality" to mapOf("not_null_assertion_count" to 6),
            ),
        )

    private object NoopLogger : StructuredLogger {
        override fun info(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit

        override fun error(
            event: String,
            fields: Map<String, Any?>,
        ) = Unit
    }
}
