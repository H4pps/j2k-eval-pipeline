package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.logging.StructuredLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvaluatorRunnerTest {
    @Test
    fun `evaluator succeeds without generated kotlin output and writes default reports`() {
        val benchmarkId = "eval-runner-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)

        val exitCode = EvaluatorRunner(logger = NoopLogger).run(EvaluationRequest(configPath, null, null))

        val reportDirectory = Path.of("build/reports/j2k-eval/$benchmarkId")
        val jsonPath = reportDirectory.resolve("evaluation.json")
        assertEquals(0, exitCode)
        assertTrue(jsonPath.exists())
        assertContains(jsonPath.readText(), "\"status\":\"completed_with_warnings\"")
        assertContains(jsonPath.readText(), "\"java_file_count\":1")
        assertContains(jsonPath.readText(), "\"kotlin_file_count\":0")
        assertContains(jsonPath.readText(), "generated_kotlin_directory_missing")
    }

    @Test
    fun `evaluator uses explicit generated kotlin and report directories`() {
        val benchmarkId = "eval-runner-explicit-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-explicit-")
        val githubSummary = Files.createTempFile("github-step-summary-", ".md")
        val conversionReport = Files.createTempFile("conversion-report-", ".json")
        val checkoutReport = Files.createTempFile("checkout-report-", ".json")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)
        writeConversionReport(conversionReport, benchmarkId, "completed", 1, 1)
        writeCheckoutReport(checkoutReport, benchmarkId, 1, "skipped")

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                    conversionReport = conversionReport,
                    checkoutReport = checkoutReport,
                    githubSummaryPath = githubSummary,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        val summary = githubSummary.readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"generated_kotlin_directory\":\"${generatedDirectory.normalize()}\"")
        assertContains(json, "\"report_directory\":\"${reportDirectory.normalize()}\"")
        assertContains(json, "\"status\":\"completed\"")
        assertContains(json, "\"kotlin_file_count\":1")
        assertContains(summary, "# J2K Evaluation Summary")
        assertContains(summary, "## Result Interpretation")
        assertContains(summary, "## Structural Preservation")
        assertContains(summary, "Kotlin-only API names")
        assertContains(summary, "Static J2K generated Kotlin for every configured Java input")
    }

    @Test
    fun `evaluator warns when generated kotlin file is missing`() {
        val benchmarkId = "eval-runner-missing-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-missing-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-missing-")
        val conversionReport = Files.createTempFile("conversion-report-missing-", ".json")
        val checkoutReport = Files.createTempFile("checkout-report-missing-", ".json")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        checkoutDirectory.resolve("src/main/java/com/example/Missing.java").writeText("package com.example; class Missing {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)
        writeConversionReport(conversionReport, benchmarkId, "partial", 2, 1, errors = listOf("com/example/Missing.java: failed"))
        writeCheckoutReport(checkoutReport, benchmarkId, 2, "skipped")

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                    conversionReport = conversionReport,
                    checkoutReport = checkoutReport,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"status\":\"completed_with_warnings\"")
        assertContains(json, "generated_kotlin_file_missing")
        assertContains(json, "com/example/Missing.kt")
        assertContains(json, "\"conversion_failure\"")
    }

    @Test
    fun `evaluator warns when generated kotlin file is unexpected`() {
        val benchmarkId = "eval-runner-extra-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-extra-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-extra-")
        val conversionReport = Files.createTempFile("conversion-report-extra-", ".json")
        val checkoutReport = Files.createTempFile("checkout-report-extra-", ".json")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        generatedDirectory.resolve("com/example/Extra.kt").writeText("package com.example\nclass Extra")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)
        writeConversionReport(conversionReport, benchmarkId, "completed", 1, 2)
        writeCheckoutReport(checkoutReport, benchmarkId, 1, "skipped")

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                    conversionReport = conversionReport,
                    checkoutReport = checkoutReport,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"status\":\"completed_with_warnings\"")
        assertContains(json, "generated_kotlin_file_unexpected")
        assertContains(json, "com/example/Extra.kt")
    }

    @Test
    fun `evaluator reports missing checkout and conversion reports as warnings`() {
        val benchmarkId = "eval-runner-no-reports-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-no-reports-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-no-reports-")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                    conversionReport = Files.createTempDirectory("missing-report-parent").resolve("conversion.json"),
                    checkoutReport = Files.createTempDirectory("missing-report-parent").resolve("checkout.json"),
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "checkout_report_missing")
        assertContains(json, "conversion_report_missing")
    }

    @Test
    fun `evaluator warns when metadata reports belong to another benchmark`() {
        val benchmarkId = "eval-runner-mismatch-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-mismatch-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-mismatch-")
        val conversionReport = Files.createTempFile("conversion-report-mismatch-", ".json")
        val checkoutReport = Files.createTempFile("checkout-report-mismatch-", ".json")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)
        writeConversionReport(conversionReport, "other-benchmark", "completed", 1, 1)
        writeCheckoutReport(checkoutReport, "other-benchmark", 1, "skipped")

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                    conversionReport = conversionReport,
                    checkoutReport = checkoutReport,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "checkout_report_benchmark_mismatch")
        assertContains(json, "conversion_report_benchmark_mismatch")
    }

    /**
     * Writes a complete benchmark YAML config for evaluator runner tests.
     */
    private fun writeConfig(
        id: String,
        checkoutDirectory: Path,
    ): Path {
        val configPath = Files.createTempFile("benchmark-config-", ".yml")
        configPath.writeText(
            """
            id: $id
            name: Test Benchmark
            role: primary
            description: test
            repository:
              upstream: https://example.com/upstream
              source: https://example.com/source
              ref: abcdef
              branch: main
            checkout:
              directory: ${checkoutDirectory.normalize().toString().replace('\\', '/')}
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

    /**
     * Writes a conversion report fixture for evaluator tests.
     */
    private fun writeConversionReport(
        path: Path,
        id: String,
        status: String,
        sourceJavaFileCount: Int,
        generatedKotlinFileCount: Int,
        errors: List<String> = emptyList(),
    ) {
        path.writeText(
            """
            {
              "benchmark": {"id": "$id"},
              "status": "$status",
              "counts": {
                "source_java_file_count": $sourceJavaFileCount,
                "generated_kotlin_file_count": $generatedKotlinFileCount,
                "warning_count": 0,
                "error_count": ${errors.size}
              },
              "warnings": [],
              "errors": [${errors.joinToString(",") { "\"$it\"" }}]
            }
            """.trimIndent(),
        )
    }

    /**
     * Writes a checkout report fixture for evaluator tests.
     */
    private fun writeCheckoutReport(
        path: Path,
        id: String,
        javaFileCount: Int,
        buildStatus: String,
    ) {
        path.writeText(
            """
            {
              "benchmark": {"id": "$id"},
              "checkout_directory": "build/benchmarks/$id/source",
              "counts": {"java_file_count": $javaFileCount},
              "build_status": "$buildStatus",
              "run_build": false
            }
            """.trimIndent(),
        )
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
