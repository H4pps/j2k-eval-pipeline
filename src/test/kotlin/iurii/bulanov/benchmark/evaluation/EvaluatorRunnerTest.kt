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
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"generated_kotlin_directory\":\"${generatedDirectory.normalize()}\"")
        assertContains(json, "\"report_directory\":\"${reportDirectory.normalize()}\"")
        assertContains(json, "\"status\":\"completed\"")
        assertContains(json, "\"kotlin_file_count\":1")
    }

    @Test
    fun `evaluator warns when generated kotlin file is missing`() {
        val benchmarkId = "eval-runner-missing-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-missing-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-missing-")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        checkoutDirectory.resolve("src/main/java/com/example/Missing.java").writeText("package com.example; class Missing {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"status\":\"completed_with_warnings\"")
        assertContains(json, "generated_kotlin_file_missing")
        assertContains(json, "com/example/Missing.kt")
    }

    @Test
    fun `evaluator warns when generated kotlin file is unexpected`() {
        val benchmarkId = "eval-runner-extra-${System.nanoTime()}"
        val checkoutDirectory = Path.of("build/benchmarks/$benchmarkId/source")
        val generatedDirectory = Files.createTempDirectory("generated-kotlin-extra-")
        val reportDirectory = Files.createTempDirectory("evaluation-report-extra-")
        checkoutDirectory.resolve("src/main/java/com/example").createDirectories()
        checkoutDirectory.resolve("src/main/java/com/example/App.java").writeText("package com.example; class App {}")
        generatedDirectory.resolve("com/example").createDirectories()
        generatedDirectory.resolve("com/example/App.kt").writeText("package com.example\nclass App")
        generatedDirectory.resolve("com/example/Extra.kt").writeText("package com.example\nclass Extra")
        val configPath = writeConfig(benchmarkId, checkoutDirectory)

        val exitCode =
            EvaluatorRunner(logger = NoopLogger).run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedDirectory,
                    reportDirectory = reportDirectory,
                ),
            )

        val json = reportDirectory.resolve("evaluation.json").readText()
        assertEquals(0, exitCode)
        assertContains(json, "\"status\":\"completed_with_warnings\"")
        assertContains(json, "generated_kotlin_file_unexpected")
        assertContains(json, "com/example/Extra.kt")
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
