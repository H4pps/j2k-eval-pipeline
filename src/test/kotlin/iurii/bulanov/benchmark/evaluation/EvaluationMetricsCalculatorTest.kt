package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Files
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
            "package com.example; public class App { public String name() { return \"x\"; } public String getTitle() { return \"x\"; } }",
        )
        missingJava.writeText("package com.example; public class Missing {}")
        utilityJava.writeText("package com.example; public final class Utility {}")
        appKotlin.writeText("package com.example\nclass App { fun name(input: Any?) = call(input!!); fun newHelper() = Unit }")
        utilityKotlin.writeText("package com.example\nobject Utility")
        extraKotlin.writeText("")

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles =
                    listOf(
                        DiscoveredSourceFile(appJava, root.resolve("checkout").relativize(appJava)),
                        DiscoveredSourceFile(missingJava, root.resolve("checkout").relativize(missingJava)),
                        DiscoveredSourceFile(utilityJava, root.resolve("checkout").relativize(utilityJava)),
                    ),
                kotlinFiles =
                    listOf(
                        DiscoveredSourceFile(appKotlin, root.resolve("generated").relativize(appKotlin)),
                        DiscoveredSourceFile(utilityKotlin, root.resolve("generated").relativize(utilityKotlin)),
                        DiscoveredSourceFile(extraKotlin, root.resolve("generated").relativize(extraKotlin)),
                    ),
                sourceRoots = listOf("src/main/java"),
            )

        assertEquals(3, metrics.fileCoverage.javaFileCount)
        assertEquals(3, metrics.fileCoverage.kotlinFileCount)
        assertEquals(2, metrics.fileCoverage.matchedKotlinFileCount)
        assertContains(metrics.fileCoverage.missingKotlinFiles, "com/example/Missing.kt")
        assertContains(metrics.fileCoverage.unexpectedKotlinFiles, "com/example/Extra.kt")
        assertContains(metrics.fileCoverage.emptyGeneratedFiles, "com/example/Extra.kt")
        assertEquals(2, metrics.fileCoverage.packagePreservedCount)
        assertTrue(metrics.structure.javaTopLevelDeclarationCount >= 2)
        assertTrue(metrics.structure.kotlinTopLevelDeclarationCount >= 1)
        assertContains(metrics.structure.missingPublicApiNames, "Missing")
        assertContains(metrics.structure.kotlinOnlyPublicApiNames, "newHelper")
        assertContains(metrics.structure.nameDiffs.classLike.missingInKotlin, "Missing")
        assertContains(metrics.structure.nameDiffs.classLikeToObjectNames, "Utility")
        assertContains(metrics.structure.nameDiffs.javaBeanAccessorNames, "getTitle")
        assertFalse("getTitle" in metrics.structure.nameDiffs.functions.missingInKotlin)
        assertContains(metrics.structure.nameDiffs.functions.kotlinOnly, "newHelper")
        assertEquals(1, metrics.quality.notNullAssertionCount)
        assertEquals(1, metrics.quality.notNullAssertionInCallCount)
        assertEquals(1, metrics.quality.anyNullableCount)
    }
}
