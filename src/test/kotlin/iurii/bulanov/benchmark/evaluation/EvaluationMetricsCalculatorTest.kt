package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationMetricsCalculatorTest {
    @Test
    fun `calculates file coverage structure and quality metrics`() {
        val fixture = createMetricsFixture()
        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles = fixture.javaFiles,
                kotlinFiles = fixture.kotlinFiles,
                sourceRoots = listOf("src/main/java"),
            )

        assertFileCoverage(metrics)
        assertStructure(metrics)
        assertQuality(metrics)
    }

    @Test
    fun `keeps accessors missing when matching properties are private or on another type`() {
        val root = Files.createTempDirectory("evaluation-accessor-scope-")
        val javaRoot = root.resolve("checkout/src/main/java/com/example").apply { createDirectories() }
        val kotlinRoot = root.resolve("generated/com/example").apply { createDirectories() }
        val appJava = javaRoot.resolve("App.java")
        val appKotlin = kotlinRoot.resolve("App.kt")
        appJava.writeText(
            """
            package com.example;
            public class App {
              public String getHidden() { return "x"; }
              public String getTitle() { return "x"; }
            }
            """.trimIndent(),
        )
        appKotlin.writeText(
            """
            package com.example
            class App { private val hidden: String = "x" }
            class Other { val title: String = "x" }
            """.trimIndent(),
        )

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles = listOf(discovered(appJava, root.resolve("checkout"))),
                kotlinFiles = listOf(discovered(appKotlin, root.resolve("generated"))),
                sourceRoots = listOf("src/main/java"),
            )

        assertContains(metrics.structure.nameDiffs.functions.missingInKotlin, "getHidden")
        assertContains(metrics.structure.nameDiffs.functions.missingInKotlin, "getTitle")
        assertFalse("getHidden" in metrics.structure.nameDiffs.javaBeanAccessorNames)
        assertFalse("getTitle" in metrics.structure.nameDiffs.javaBeanAccessorNames)
    }

    /**
     * Creates a temporary source fixture with matched, missing, and unexpected outputs.
     */
    private fun createMetricsFixture(): MetricsFixture {
        val root = Files.createTempDirectory("evaluation-metrics-")
        val javaRoot = root.resolve("checkout/src/main/java/com/example")
        val kotlinRoot = root.resolve("generated/com/example")
        javaRoot.createDirectories()
        kotlinRoot.createDirectories()
        val appJava = javaRoot.resolve("App.java")
        val missingJava = javaRoot.resolve("Missing.java")
        val utilityJava = javaRoot.resolve("Utility.java")
        val appKotlin = kotlinRoot.resolve("App.kt")
        val utilityKotlin = kotlinRoot.resolve("Utility.kt")
        val extraKotlin = kotlinRoot.resolve("Extra.kt")
        appJava.writeText(
            """
            package com.example;
            public class App {
              public String name() { return "x"; }
              public String getTitle() { return "x"; }
              public void setEnabled(boolean enabled) {}
              public boolean isReady() { return true; }
            }
            """.trimIndent(),
        )
        missingJava.writeText("package com.example; public class Missing {}")
        utilityJava.writeText("package com.example; public final class Utility {}")
        appKotlin.writeText(
            """
            package com.example
            class App {
              val title: String = "x"
              var enabled: Boolean = true
              fun name(input: Any?) = call(input!!)
              fun newHelper() = Unit
            }
            """.trimIndent(),
        )
        utilityKotlin.writeText("package com.example\nobject Utility")
        extraKotlin.writeText("")

        return MetricsFixture(
            javaFiles =
                listOf(
                    discovered(appJava, root.resolve("checkout")),
                    discovered(missingJava, root.resolve("checkout")),
                    discovered(utilityJava, root.resolve("checkout")),
                ),
            kotlinFiles =
                listOf(
                    discovered(appKotlin, root.resolve("generated")),
                    discovered(utilityKotlin, root.resolve("generated")),
                    discovered(extraKotlin, root.resolve("generated")),
                ),
        )
    }

    /**
     * Asserts coverage and package preservation metrics.
     */
    private fun assertFileCoverage(metrics: EvaluationMetrics) {
        assertEquals(3, metrics.fileCoverage.javaFileCount)
        assertEquals(3, metrics.fileCoverage.kotlinFileCount)
        assertEquals(2, metrics.fileCoverage.matchedKotlinFileCount)
        assertContains(metrics.fileCoverage.missingKotlinFiles, "com/example/Missing.kt")
        assertContains(metrics.fileCoverage.unexpectedKotlinFiles, "com/example/Extra.kt")
        assertContains(metrics.fileCoverage.emptyGeneratedFiles, "com/example/Extra.kt")
        assertEquals(2, metrics.fileCoverage.packagePreservedCount)
    }

    /**
     * Asserts parser-backed structural and property mapping metrics.
     */
    private fun assertStructure(metrics: EvaluationMetrics) {
        assertTrue(metrics.structure.javaTopLevelDeclarationCount >= 2)
        assertTrue(metrics.structure.kotlinTopLevelDeclarationCount >= 1)
        assertContains(metrics.structure.missingPublicApiNames, "Missing")
        assertContains(metrics.structure.missingPublicApiNames, "isReady")
        assertFalse("getTitle" in metrics.structure.missingPublicApiNames)
        assertFalse("setEnabled" in metrics.structure.missingPublicApiNames)
        assertContains(metrics.structure.kotlinOnlyPublicApiNames, "newHelper")
        assertFalse("title" in metrics.structure.kotlinOnlyPublicApiNames)
        assertFalse("enabled" in metrics.structure.kotlinOnlyPublicApiNames)
        assertContains(metrics.structure.nameDiffs.classLike.missingInKotlin, "Missing")
        assertContains(metrics.structure.nameDiffs.classLikeToObjectNames, "Utility")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "getTitle")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "setEnabled")
        assertFalse("getTitle" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertFalse("setEnabled" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertContains(metrics.structure.nameDiffs.functions.missingInKotlin, "isReady")
        assertContains(metrics.structure.nameDiffs.functions.kotlinOnly, "newHelper")
    }

    /**
     * Asserts parser-backed Kotlin quality metrics.
     */
    private fun assertQuality(metrics: EvaluationMetrics) {
        assertEquals(1, metrics.quality.notNullAssertionCount)
        assertEquals(1, metrics.quality.notNullAssertionInCallCount)
        assertEquals(1, metrics.quality.anyNullableCount)
    }

    /**
     * Builds a discovered source file relative to [root].
     */
    private fun discovered(
        path: Path,
        root: Path,
    ): DiscoveredSourceFile = DiscoveredSourceFile(path, root.relativize(path))

    private data class MetricsFixture(
        val javaFiles: List<DiscoveredSourceFile>,
        val kotlinFiles: List<DiscoveredSourceFile>,
    )
}
