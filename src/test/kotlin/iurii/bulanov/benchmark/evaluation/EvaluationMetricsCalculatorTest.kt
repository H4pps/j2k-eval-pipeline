package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        val appKotlin = kotlinRoot.resolve("App.kt")
        val extraKotlin = kotlinRoot.resolve("Extra.kt")
        appJava.writeText("package com.example; public class App { public String name() { return \"x\"; } }")
        missingJava.writeText("package com.example; public class Missing {}")
        appKotlin.writeText("package com.example\nclass App { fun name(input: Any?) = call(input!!) }")
        extraKotlin.writeText("")

        val metrics =
            EvaluationMetricsCalculator().calculate(
                javaFiles =
                    listOf(
                        DiscoveredSourceFile(appJava, root.resolve("checkout").relativize(appJava)),
                        DiscoveredSourceFile(missingJava, root.resolve("checkout").relativize(missingJava)),
                    ),
                kotlinFiles =
                    listOf(
                        DiscoveredSourceFile(appKotlin, root.resolve("generated").relativize(appKotlin)),
                        DiscoveredSourceFile(extraKotlin, root.resolve("generated").relativize(extraKotlin)),
                    ),
                sourceRoots = listOf("src/main/java"),
            )

        assertEquals(2, metrics.fileCoverage.javaFileCount)
        assertEquals(2, metrics.fileCoverage.kotlinFileCount)
        assertEquals(1, metrics.fileCoverage.matchedKotlinFileCount)
        assertContains(metrics.fileCoverage.missingKotlinFiles, "com/example/Missing.kt")
        assertContains(metrics.fileCoverage.unexpectedKotlinFiles, "com/example/Extra.kt")
        assertContains(metrics.fileCoverage.emptyGeneratedFiles, "com/example/Extra.kt")
        assertEquals(1, metrics.fileCoverage.packagePreservedCount)
        assertTrue(metrics.structure.javaTopLevelDeclarationCount >= 2)
        assertTrue(metrics.structure.kotlinTopLevelDeclarationCount >= 1)
        assertEquals(1, metrics.quality.notNullAssertionCount)
        assertEquals(1, metrics.quality.notNullAssertionInCallCount)
        assertEquals(1, metrics.quality.anyNullableCount)
    }
}
