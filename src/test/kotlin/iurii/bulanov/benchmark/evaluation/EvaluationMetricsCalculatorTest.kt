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
        assertContent(metrics)
        assertNullability(metrics)
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

    @Test
    fun `maps private and static Java accessors converted to Kotlin properties`() {
        val root = Files.createTempDirectory("evaluation-accessor-properties-")
        val javaRoot = root.resolve("checkout/src/main/java/com/example").apply { createDirectories() }
        val kotlinRoot = root.resolve("generated/com/example").apply { createDirectories() }
        val appJava = javaRoot.resolve("App.java")
        val appKotlin = kotlinRoot.resolve("App.kt")
        appJava.writeText(
            """
            package com.example;
            public class App {
              private String getHidden() { return "x"; }
              static String getGlobalName() { return "x"; }
            }
            """.trimIndent(),
        )
        appKotlin.writeText(
            """
            package com.example
            val globalName: String = "x"
            class App { private val hidden: String = "x" }
            """.trimIndent(),
        )

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles = listOf(discovered(appJava, root.resolve("checkout"))),
                kotlinFiles = listOf(discovered(appKotlin, root.resolve("generated"))),
                sourceRoots = listOf("src/main/java"),
            )

        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "getHidden")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "getGlobalName")
        assertFalse("getHidden" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertFalse("getGlobalName" in metrics.structure.nameDiffs.functions.missingInKotlin)
    }

    @Test
    fun `calculates core content preservation scores`() {
        val root = Files.createTempDirectory("evaluation-core-content-")
        val javaRoot = root.resolve("checkout/src/main/java/com/example").apply { createDirectories() }
        val kotlinRoot = root.resolve("generated/com/example").apply { createDirectories() }
        val appJava = javaRoot.resolve("App.java")
        val appKotlin = kotlinRoot.resolve("App.kt")
        appJava.writeText(coreContentJava())
        appKotlin.writeText(coreContentKotlin())

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles = listOf(discovered(appJava, root.resolve("checkout"))),
                kotlinFiles = listOf(discovered(appKotlin, root.resolve("generated"))),
                sourceRoots = listOf("src/main/java"),
            )

        assertEquals(0.8, metrics.content.controlFlowFidelityScore, DOUBLE_TOLERANCE)
        assertEquals(3, metrics.content.javaFunctionDeclarationCount)
        assertEquals(3, metrics.content.kotlinFunctionDeclarationCount)
        assertEquals(0, metrics.content.contentShapePreservedFileCount)
        assertEquals(1, metrics.content.contentShapeMismatchFileCount)
        assertEquals(1.0, metrics.content.returnPreservationRatio, DOUBLE_TOLERANCE)
        assertEquals(1.0, metrics.content.branchPreservationRatio, DOUBLE_TOLERANCE)
        assertEquals(0.0, metrics.content.throwPreservationRatio, DOUBLE_TOLERANCE)
        assertEquals(1.0, metrics.content.tryPreservationRatio, DOUBLE_TOLERANCE)
        assertEquals(0.0, metrics.content.contentShapePreservationRate, DOUBLE_TOLERANCE)
        assertEquals(2.0 / 3.0, metrics.content.javaReturnDensity, DOUBLE_TOLERANCE)
        assertEquals(2.0 / 3.0, metrics.content.kotlinReturnDensity, DOUBLE_TOLERANCE)
        assertEquals(1.0, metrics.content.returnStatementDensityPreservation, DOUBLE_TOLERANCE)
        assertEquals(1.0, metrics.content.javaBranchComplexityIndex, DOUBLE_TOLERANCE)
        assertEquals(2.0 / 3.0, metrics.content.kotlinBranchComplexityIndex, DOUBLE_TOLERANCE)
        assertEquals(2.0 / 3.0, metrics.content.branchComplexityIndexPreservation, DOUBLE_TOLERANCE)
    }

    /**
     * Java fixture for exact content metric formulas.
     */
    private fun coreContentJava(): String =
        """
        package com.example;
        public class App {
          public String one() {
            if (true) return "a";
            return "b";
          }
          public void two() {
            try {
              throw new RuntimeException();
            } catch (RuntimeException ignored) {
            }
          }
          public void three() {
            while (false) {}
          }
        }
        """.trimIndent()

    /**
     * Kotlin fixture for exact content metric formulas.
     */
    private fun coreContentKotlin(): String =
        """
        package com.example
        class App {
          fun one(): String {
            if (true) return "a"
            return "b"
          }
          fun two() {
            try {
            } catch (ignored: RuntimeException) {
            }
          }
          fun three() {}
        }
        """.trimIndent()

    @Test
    fun `calculates nullability inference accuracy from contradictory casts`() {
        val root = Files.createTempDirectory("evaluation-nullability-accuracy-")
        val javaRoot = root.resolve("checkout/src/main/java/com/example").apply { createDirectories() }
        val kotlinRoot = root.resolve("generated/com/example").apply { createDirectories() }
        val appJava = javaRoot.resolve("App.java")
        val appKotlin = kotlinRoot.resolve("App.kt")
        appJava.writeText(
            """
            package com.example;
            public class App {
              public Object lookup(Object source) { return source; }
            }
            """.trimIndent(),
        )
        appKotlin.writeText(
            """
            package com.example
            class App {
              fun lookup(source: Any?): String? {
                val value = source as String
                if (value == null) return null
                return source?.toString()
              }
            }
            """.trimIndent(),
        )

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles = listOf(discovered(appJava, root.resolve("checkout"))),
                kotlinFiles = listOf(discovered(appKotlin, root.resolve("generated"))),
                sourceRoots = listOf("src/main/java"),
            )

        assertEquals(1, metrics.nullability.contradictoryNullabilityPatterns)
        assertEquals(1, metrics.nullability.nullComparisonCount)
        assertEquals(1, metrics.nullability.nullabilityCastCount)
        assertEquals(1, metrics.nullability.safeCallCount)
        assertEquals(3, metrics.nullability.totalNullabilityOperationCount)
        assertEquals(2.0 / 3.0, metrics.nullability.nullabilityInferenceAccuracy, DOUBLE_TOLERANCE)
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
              @Nullable public String maybeName() { return null; }
              @NotNull public String strictName() { return "x"; }
              public String lostBody() { return "body"; }
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
              fun maybeName(): String = "x"
              fun strictName(): String? = "x"
              fun lostBody() {}
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
        assertFalse("Missing" in metrics.structure.missingPublicApiNames)
        assertContains(metrics.structure.missingPublicApiNames, "isReady")
        assertFalse("getTitle" in metrics.structure.missingPublicApiNames)
        assertFalse("setEnabled" in metrics.structure.missingPublicApiNames)
        assertContains(metrics.structure.kotlinOnlyPublicApiNames, "newHelper")
        assertFalse("title" in metrics.structure.kotlinOnlyPublicApiNames)
        assertFalse("enabled" in metrics.structure.kotlinOnlyPublicApiNames)
        assertFalse("Missing" in metrics.structure.nameDiffs.classLike.missingInKotlin)
        assertContains(metrics.structure.nameDiffs.classLikeToObjectNames, "Utility")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "getTitle")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "setEnabled")
        assertFalse("getTitle" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertFalse("setEnabled" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertContains(metrics.structure.nameDiffs.functions.missingInKotlin, "isReady")
        assertContains(metrics.structure.nameDiffs.functions.kotlinOnly, "newHelper")
    }

    /**
     * Asserts parser-backed content preservation metrics.
     */
    private fun assertContent(metrics: EvaluationMetrics) {
        assertEquals(2, metrics.content.matchedFileCount)
        assertContains(metrics.content.missingKotlinBodies, "com/example/App.kt#lostBody")
        assertFalse("com/example/App.kt#getTitle" in metrics.content.missingKotlinBodies)
        assertFalse("com/example/App.kt#setEnabled" in metrics.content.missingKotlinBodies)
        assertContains(metrics.content.contentShapeMismatchFiles, "com/example/App.kt")
        assertTrue(metrics.content.javaNonEmptyMethodCount > metrics.content.kotlinNonEmptyFunctionCount)
    }

    /**
     * Asserts parser-backed nullability preservation metrics.
     */
    private fun assertNullability(metrics: EvaluationMetrics) {
        assertEquals(1, metrics.nullability.javaNullableAnnotationCount)
        assertEquals(1, metrics.nullability.javaNotNullAnnotationCount)
        assertEquals(2, metrics.nullability.kotlinNullableTypeCount)
        assertContains(metrics.nullability.nullableAnnotationsNotPreserved, "com/example/App.kt#maybeName")
        assertContains(metrics.nullability.notNullAnnotationsBecameNullable, "com/example/App.kt#strictName")
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

    private companion object {
        private const val DOUBLE_TOLERANCE = 0.0001
    }
}
