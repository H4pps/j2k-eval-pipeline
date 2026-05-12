package iurii.bulanov.benchmark.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceTextScannerTest {
    @Test
    fun `scans java structural declarations with JavaParser`() {
        val scanner = SourceTextScanner()

        val java =
            scanner.scanJava(
                """
                package com.example;
                public class App {
                  private String ignored = "class Ghost { public void fake() {} }";
                  // interface Ignored {}
                  public String getName() { return "name"; }
                }
                record User(String id) {
                  public String displayName() { return id; }
                }
                interface Named {}
                enum Mode { ON }
                """.trimIndent(),
            )

        assertEquals("com.example", java.packageName)
        assertEquals(2, java.classLikeCount)
        assertEquals(1, java.interfaceCount)
        assertEquals(1, java.enumCount)
        assertTrue("App" in java.classLikeNames)
        assertTrue("User" in java.classLikeNames)
        assertTrue("Named" in java.interfaceNames)
        assertTrue("Mode" in java.enumNames)
        assertTrue("displayName" in java.functionNames)
        assertTrue("App" in java.publicApiNames)
        assertFalse("Ghost" in java.publicApiNames)
        assertFalse("fake" in java.publicApiNames)
        assertEquals(setOf("name"), java.javaBeanAccessorPropertyNames["getName"])
    }

    @Test
    fun `scans kotlin structural declarations with Kotlin PSI`() {
        val kotlin =
            SourceTextScanner().scanKotlin(
                """
                package com.example
                class App(val title: String) {
                  val name: String = "name"
                  private val ignored = "fun fake() = Unit"
                  fun run(): String = name
                }
                interface Named
                enum class Mode { ON }
                object Singleton
                """.trimIndent(),
            )

        assertEquals("com.example", kotlin.packageName)
        assertEquals(1, kotlin.classLikeCount)
        assertEquals(1, kotlin.interfaceCount)
        assertEquals(1, kotlin.enumCount)
        assertEquals(1, kotlin.objectCount)
        assertTrue("App" in kotlin.classLikeNames)
        assertTrue("Named" in kotlin.interfaceNames)
        assertTrue("Mode" in kotlin.enumNames)
        assertTrue("Singleton" in kotlin.objectNames)
        assertTrue("name" in kotlin.publicApiNames)
        assertTrue("title" in kotlin.propertyNames)
        assertTrue("run" in kotlin.functionNames)
        assertFalse("ignored" in kotlin.propertyNames)
        assertFalse("fake" in kotlin.functionNames)
    }

    @Test
    fun `scans kotlin quality warning categories with Kotlin PSI`() {
        val metrics =
            SourceTextScanner().scanKotlinQuality(
                path = "com/example/App.kt",
                source =
                    """
                    package com.example
                    // TODO() and foo.getIgnored() inside comments must not count.
                    import missing.Symbol
                    class App {
                      private val ignored = "TODO() value!! Any? foo.getIgnored()"
                      val name: String = getCurrentUser().name
                      fun run(value: Any?, foo: Foo?) {
                        call(value!!)
                        if (foo?.isEnabled != true) TODO()
                        java.util.Arrays.toString(arrayOf(1))
                        val optional = java.util.Optional.empty<String>()
                        val type = Class.forName("com.example.App")
                        val javaClass = App::class.java
                        foo.getName()
                        foo?.setName("name")
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
        assertEquals(2, metrics.getterSetterCallCount)
        assertEquals(1, metrics.nullableBooleanComparisonCount)
        assertEquals(1, metrics.eagerPropertyInitializationCount)
    }
}
