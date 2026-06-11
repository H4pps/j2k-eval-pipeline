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
        assertContains(md, "| Control-flow fidelity | 0.800 | 0.950 |")
        assertContains(md, "| Nullability inference accuracy | 2/3 (66.7%) | 2/2 (100.0%) |")
        assertContains(md, "| Status | partial | completed |")
        assertContains(md, "| Java function declarations | 56 | 56 |")
        assertCountFirstContentRows(md)
        assertContains(md, "| Null comparisons | 1 | 1 |")
        assertContains(md, "| Nullability casts | 1 | 1 |")
        assertContains(md, "| Safe calls | 1 | 0 |")
        assertContains(md, "| Total nullability operations | 3 | 2 |")
        assertContains(
            md,
            "| Nullability inference accuracy (non-contradictory operations / total operations) | " +
                "2/3 (66.7%) | 2/2 (100.0%) |",
        )
        // Divergence: a file missing in one kind but produced by the other.
        assertContains(md, "## Divergences")
        assertContains(md, "`a/B.kt` — missing in `k1-old-dumb`")

        assertContains(json, "\"kinds\":[\"k1-old-dumb\", \"k2\"]")
        assertContains(json, "\"missing_kinds\":[\"k1-new\"]")
        assertContains(json, "\"coverage_percent\"")
    }

    private fun assertCountFirstContentRows(md: String) {
        assertContains(
            md,
            "| Content shape preserved files (preserved/matched files) | 14/16 (87.5%) | 17/17 (100.0%) |",
        )
        assertContains(
            md,
            "| Returns preserved (Kotlin/Java returns) | 40/50 (80.0%) | 57/60 (95.0%) |",
        )
        assertContains(
            md,
            "| Branches preserved (Kotlin/Java branches) | 12/10 (100.0%, capped) | 10/10 (100.0%) |",
        )
        assertContains(md, "| Throws preserved (Kotlin/Java throws) | 3/4 (75.0%) | 4/4 (100.0%) |")
        assertContains(
            md,
            "| Try blocks preserved (Kotlin/Java try blocks) | 2/2 (100.0%) | 2/2 (100.0%) |",
        )
        assertContains(
            md,
            "| Java return rate (Java returns/functions) | 50/56 = 0.893 | 60/56 = 1.071 |",
        )
        assertContains(
            md,
            "| Kotlin return rate (Kotlin returns/functions) | 40/48 = 0.833 | 57/56 = 1.018 |",
        )
        assertContains(
            md,
            "| Return rate preserved (Kotlin/Java return rate) | 0.833/0.893 (93.3%) | 1.018/1.071 (95.0%) |",
        )
        assertContains(
            md,
            "| Java control-flow rate ((branches + loops + tries)/functions) | 17/56 = 0.304 | 15/56 = 0.268 |",
        )
        assertContains(
            md,
            "| Kotlin control-flow rate ((branches + loops + tries)/functions) | 22/48 = 0.458 | 15/56 = 0.268 |",
        )
        assertContains(
            md,
            "| Control-flow rate preserved (Kotlin/Java control-flow rate) | " +
                "0.458/0.304 (100.0%, capped) | 0.268/0.268 (100.0%) |",
        )
    }

    @Test
    fun `renders missing count-first content fields as dash`() {
        val comparison =
            Comparison(
                benchmarkId = "sample",
                benchmarkName = "Sample",
                kinds =
                    listOf(
                        KindEvaluation(
                            ConverterKind.K2,
                            mapOf("content" to mapOf("return_preservation_ratio" to 1.0)),
                        ),
                    ),
                missingKinds = emptyList(),
            )

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)

        assertContains(md, "| Content shape preserved files (preserved/matched files) | — |")
        assertContains(md, "| Returns preserved (Kotlin/Java returns) | — |")
        assertContains(md, "| Java return rate (Java returns/functions) | — |")
        assertContains(md, "| Kotlin return rate (Kotlin returns/functions) | — |")
        assertContains(md, "| Return rate preserved (Kotlin/Java return rate) | — |")
        assertContains(md, "| Java control-flow rate ((branches + loops + tries)/functions) | — |")
        assertContains(md, "| Kotlin control-flow rate ((branches + loops + tries)/functions) | — |")
        assertContains(md, "| Control-flow rate preserved (Kotlin/Java control-flow rate) | — |")
        assertContains(md, "| Missing outputs | — |")
        assertContains(md, "| Missing Kotlin bodies | — |")
        assertContains(md, "| Nullability inference accuracy (non-contradictory operations / total operations) | — |")
    }

    @Test
    fun `renders capped suffix for zero java baselines`() {
        val comparison =
            Comparison(
                benchmarkId = "sample",
                benchmarkName = "Sample",
                kinds =
                    listOf(
                        KindEvaluation(
                            ConverterKind.K2,
                            mapOf(
                                "content" to
                                    mapOf(
                                        "matched_file_count" to 1,
                                        "content_shape_preserved_file_count" to 1,
                                        "content_shape_preservation_rate" to 1.0,
                                        "java_return_count" to 0,
                                        "kotlin_return_count" to 1,
                                        "return_preservation_ratio" to 1.0,
                                        "java_branch_count" to 0,
                                        "kotlin_branch_count" to 1,
                                        "branch_preservation_ratio" to 1.0,
                                        "java_throw_count" to 0,
                                        "kotlin_throw_count" to 1,
                                        "throw_preservation_ratio" to 1.0,
                                        "java_try_count" to 0,
                                        "kotlin_try_count" to 1,
                                        "try_preservation_ratio" to 1.0,
                                        "java_function_declaration_count" to 1,
                                        "kotlin_function_declaration_count" to 1,
                                        "java_loop_count" to 0,
                                        "kotlin_loop_count" to 1,
                                        "return_statement_density_preservation" to 1.0,
                                        "branch_complexity_index_preservation" to 1.0,
                                    ),
                            ),
                        ),
                    ),
                missingKinds = emptyList(),
            )

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)

        assertContains(md, "| Returns preserved (Kotlin/Java returns) | 1/0 (100.0%, capped) |")
        assertContains(md, "| Branches preserved (Kotlin/Java branches) | 1/0 (100.0%, capped) |")
        assertContains(md, "| Throws preserved (Kotlin/Java throws) | 1/0 (100.0%, capped) |")
        assertContains(md, "| Try blocks preserved (Kotlin/Java try blocks) | 1/0 (100.0%, capped) |")
        assertContains(md, "| Java control-flow rate ((branches + loops + tries)/functions) | 0/1 = 0.000 |")
        assertContains(md, "| Kotlin control-flow rate ((branches + loops + tries)/functions) | 3/1 = 3.000 |")
        assertContains(
            md,
            "| Control-flow rate preserved (Kotlin/Java control-flow rate) | 3.000/0.000 (100.0%, capped) |",
        )
    }

    @Test
    fun `flags kinds with low coverage as not comparable`() {
        val comparison =
            Comparison(
                benchmarkId = "sample",
                benchmarkName = "Sample",
                kinds =
                    listOf(
                        kindEval(ConverterKind.K1_OLD_SMART, status = "partial", coverage = 2.04, matched = 1, missing = emptyList()),
                        kindEval(ConverterKind.K2, status = "completed", coverage = 100.0, matched = 17, missing = emptyList()),
                    ),
                missingKinds = emptyList(),
            )

        val md = ComparisonReportWriter(logger = NoopLogger).renderMarkdown(comparison)
        val json = ComparisonReportWriter(logger = NoopLogger).renderJson(comparison)

        assertContains(md, "> ⚠ `k1-old-smart` converted only 1/17 files (2.04%)")
        assertFalse(md.contains("⚠ `k2`"))
        assertContains(json, "\"low_coverage_kinds\":[\"k1-old-smart\"]")
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
    ): KindEvaluation {
        val isK2 = kind == ConverterKind.K2
        return KindEvaluation(
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
                "content" to contentMetrics(isK2, matched),
                "nullability" to
                    mapOf(
                        "kotlin_nullable_type_count" to 9,
                        "null_comparison_count" to 1,
                        "nullability_cast_count" to 1,
                        "safe_call_count" to if (kind == ConverterKind.K2) 0 else 1,
                        "contradictory_nullability_patterns" to if (kind == ConverterKind.K2) 0 else 1,
                        "total_nullability_operation_count" to if (kind == ConverterKind.K2) 2 else 3,
                        "nullability_inference_accuracy" to if (kind == ConverterKind.K2) 1.0 else 2.0 / 3.0,
                    ),
                "quality" to mapOf("not_null_assertion_count" to 6),
            ),
        )
    }

    private fun contentMetrics(
        isK2: Boolean,
        matched: Int,
    ): Map<String, Any> =
        if (isK2) {
            k2ContentMetrics(matched)
        } else {
            k1ContentMetrics(matched)
        }

    private fun k1ContentMetrics(matched: Int): Map<String, Any> {
        val preservedShapeFileCount = minOf(14, matched)
        return mapOf(
            "matched_file_count" to matched,
            "java_function_declaration_count" to 56,
            "kotlin_function_declaration_count" to 48,
            "content_shape_preserved_file_count" to preservedShapeFileCount,
            "content_shape_mismatch_file_count" to matched - preservedShapeFileCount,
            "content_shape_preservation_rate" to contentShapePreservationRate(preservedShapeFileCount, matched),
            "java_return_count" to 50,
            "kotlin_return_count" to 40,
            "java_branch_count" to 10,
            "kotlin_branch_count" to 12,
            "java_loop_count" to 5,
            "kotlin_loop_count" to 8,
            "java_throw_count" to 4,
            "kotlin_throw_count" to 3,
            "java_try_count" to 2,
            "kotlin_try_count" to 2,
            "return_preservation_ratio" to 0.8,
            "branch_preservation_ratio" to 1.0,
            "throw_preservation_ratio" to 0.75,
            "try_preservation_ratio" to 1.0,
            "control_flow_fidelity_score" to 0.8,
            "return_statement_density_preservation" to 14.0 / 15.0,
            "branch_complexity_index_preservation" to 1.0,
        )
    }

    private fun k2ContentMetrics(matched: Int): Map<String, Any> =
        mapOf(
            "matched_file_count" to matched,
            "java_function_declaration_count" to 56,
            "kotlin_function_declaration_count" to 56,
            "content_shape_preserved_file_count" to matched,
            "content_shape_mismatch_file_count" to 0,
            "content_shape_preservation_rate" to contentShapePreservationRate(matched, matched),
            "java_return_count" to 60,
            "kotlin_return_count" to 57,
            "java_branch_count" to 10,
            "kotlin_branch_count" to 10,
            "java_loop_count" to 3,
            "kotlin_loop_count" to 3,
            "java_throw_count" to 4,
            "kotlin_throw_count" to 4,
            "java_try_count" to 2,
            "kotlin_try_count" to 2,
            "return_preservation_ratio" to 0.95,
            "branch_preservation_ratio" to 1.0,
            "throw_preservation_ratio" to 1.0,
            "try_preservation_ratio" to 1.0,
            "control_flow_fidelity_score" to 0.95,
            "return_statement_density_preservation" to 0.95,
            "branch_complexity_index_preservation" to 1.0,
        )

    private fun contentShapePreservationRate(
        preservedShapeFileCount: Int,
        matched: Int,
    ): Double =
        if (matched == 0) {
            1.0
        } else {
            preservedShapeFileCount.toDouble() / matched.toDouble()
        }

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
