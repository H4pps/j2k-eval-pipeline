package iurii.bulanov.benchmark.comparison

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComparisonRunnerTest {
    @Test
    fun `compiles present kinds and notes missing ones`() {
        val reportDirectory = Files.createTempDirectory("comparison-")
        writeEvalReport(
            reportDirectory,
            ConverterKind.K1_OLD_DUMB,
            status = "partial",
            coverage = 94.1,
            matched = 16,
            missing = listOf("com/example/Missing.kt"),
        )
        writeEvalReport(reportDirectory, ConverterKind.K2, status = "completed", coverage = 100.0, matched = 17, missing = emptyList())
        val configPath = writeConfig("cmp-sample")

        val exitCode =
            ComparisonRunner(logger = NoopLogger).run(
                ComparisonRequest(
                    configPath = configPath,
                    kinds = ConverterKind.entries,
                    reportDirectory = reportDirectory,
                ),
            )

        assertEquals(0, exitCode)
        val md = reportDirectory.resolve("comparison.md").readText()
        val json = reportDirectory.resolve("comparison.json").readText()
        assertContains(md, "| Metric | `k1-old-dumb` | `k2` |")
        assertContains(md, "| Coverage % | 94.10% | 100.00% |")
        assertContains(md, "Missing (no evaluation report): `k1-old-smart`, `k1-new`")
        assertContains(md, "`com/example/Missing.kt` — missing in `k1-old-dumb`")
        assertContains(json, "\"kinds\":[\"k1-old-dumb\", \"k2\"]")
    }

    @Test
    fun `succeeds with a warning when no kind reports exist`() {
        val reportDirectory = Files.createTempDirectory("comparison-empty-")
        val configPath = writeConfig("cmp-empty")

        val exitCode =
            ComparisonRunner(logger = NoopLogger).run(
                ComparisonRequest(configPath = configPath, kinds = ConverterKind.entries, reportDirectory = reportDirectory),
            )

        assertEquals(0, exitCode)
        assertTrue(reportDirectory.resolve("comparison.md").readText().contains("No kind reports were available"))
    }

    /**
     * Writes a minimal per-kind `evaluation.json` under `<reportDirectory>/<kind>/`.
     */
    private fun writeEvalReport(
        reportDirectory: Path,
        kind: ConverterKind,
        status: String,
        coverage: Double,
        matched: Int,
        missing: List<String>,
    ) {
        val dir = reportDirectory.resolve(kind.id)
        dir.createDirectories()
        dir.resolve("evaluation.json").writeText(
            """
            {
              "benchmark": {"id": "cmp-sample", "kind": "${kind.id}"},
              "status": "completed_with_warnings",
              "counts": {"warning_count": 3},
              "conversion": {"status": "$status", "errors": []},
              "file_coverage": {
                "coverage_percent": $coverage,
                "java_file_count": 17,
                "matched_kotlin_file_count": $matched,
                "missing_kotlin_files": [${missing.joinToString(",") { "\"$it\"" }}]
              },
              "quality": {"not_null_assertion_count": 6}
            }
            """.trimIndent(),
        )
    }

    /**
     * Writes a minimal benchmark YAML config (only id/name are read by the comparison).
     */
    private fun writeConfig(id: String): Path {
        val configPath = Files.createTempFile("comparison-config-", ".yml")
        configPath.writeText(
            """
            id: $id
            name: Comparison Sample
            role: primary
            description: test
            repository:
              upstream: https://example.com/upstream
              source: https://example.com/source
              ref: abcdef
              branch: main
            checkout:
              directory: build/benchmarks/$id/source
            java:
              sourceRoots:
                - src/main/java
            build:
              tool: maven
              workingDirectory: .
              commands:
                - mvn -q -DskipTests package
            """.trimIndent(),
        )
        configPath.toFile().deleteOnExit()
        return configPath
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
