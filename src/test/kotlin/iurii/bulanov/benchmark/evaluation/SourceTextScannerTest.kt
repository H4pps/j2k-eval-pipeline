package iurii.bulanov.benchmark.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceTextScannerTest {
    @Test
    fun `scans java and kotlin structural declarations`() {
        val scanner = SourceTextScanner()

        val java =
            scanner.scanJava(
                """
                package com.example;
                public class App {
                  public String getName() { return "name"; }
                }
                interface Named {}
                enum Mode { ON }
                """.trimIndent(),
            )
        val kotlin =
            scanner.scanKotlin(
                """
                package com.example
                class App {
                  fun name(): String = "name"
                }
                interface Named
                enum class Mode { ON }
                """.trimIndent(),
            )

        assertEquals("com.example", java.packageName)
        assertEquals(1, java.classLikeCount)
        assertEquals(1, java.interfaceCount)
        assertEquals(1, java.enumCount)
        assertTrue("App" in java.publicApiNames)
        assertEquals("com.example", kotlin.packageName)
        assertEquals(1, kotlin.classLikeCount)
        assertEquals(1, kotlin.interfaceCount)
        assertEquals(1, kotlin.enumCount)
        assertTrue("name" in kotlin.publicApiNames)
    }

    @Test
    fun `scans kotlin quality warning categories`() {
        val metrics =
            SourceTextScanner().scanKotlinQuality(
                path = "com/example/App.kt",
                source =
                    """
                    package com.example
                    import missing.Symbol
                    import org.springframework.validation.Errors
                    class App {
                      val name: String = getCurrentUser().name
                      fun run(value: Any?, foo: Foo?) {
                        call(value!!)
                        if (foo?.isEnabled != true) TODO()
                        java.util.Arrays.toString(arrayOf(1))
                        foo.getName()
                      }
                    }
                    """.trimIndent(),
            )

        assertEquals(1, metrics.todoCount)
        assertEquals(1, metrics.notNullAssertionCount)
        assertEquals(1, metrics.notNullAssertionInCallCount)
        assertEquals(1, metrics.anyNullableCount)
        assertEquals(1, metrics.unresolvedImportCount)
        assertTrue(metrics.javaInteropReferenceCount > 0)
        assertEquals(1, metrics.getterSetterCallCount)
        assertEquals(1, metrics.nullableBooleanComparisonCount)
        assertEquals(1, metrics.eagerPropertyInitializationCount)
    }
}
