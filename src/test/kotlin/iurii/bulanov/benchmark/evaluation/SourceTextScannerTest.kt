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
                  @Nullable public String maybeName(@NotNull String input) {
                    if (input.isEmpty()) {
                      return null;
                    }
                    for (int index = 0; index < 1; index++) {
                      input = input.trim();
                    }
                    try {
                      return input;
                    } catch (RuntimeException exception) {
                      throw exception;
                    }
                  }
                  public void empty() {}
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
        assertTrue("maybeName" in java.content.nonEmptyFunctionNames)
        assertTrue("empty" in java.content.emptyFunctionNames)
        assertEquals(4, java.content.returnCount)
        assertEquals(1, java.content.branchCount)
        assertEquals(1, java.content.loopCount)
        assertEquals(1, java.content.throwCount)
        assertEquals(1, java.content.tryCount)
        assertEquals(setOf("maybeName"), java.nullability.nullableNames)
        assertEquals(setOf("input"), java.nullability.notNullNames)
    }

    @Test
    fun `scans kotlin structural declarations with Kotlin PSI`() {
        val kotlin =
            SourceTextScanner().scanKotlin(
                """
                package com.example
                class App(val title: String) {
                  val name: String = "name"
                  val nullableName: String? = null
                  private val ignored = "fun fake() = Unit"
                  fun run(input: String?): String? {
                    if (input == null) return null
                    for (index in 0..1) {
                      println(index)
                    }
                    try {
                      return input
                    } catch (exception: RuntimeException) {
                      throw exception
                    }
                  }
                  fun empty() {}
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
        assertTrue("run" in kotlin.content.nonEmptyFunctionNames)
        assertTrue("empty" in kotlin.content.emptyFunctionNames)
        assertEquals(2, kotlin.content.functionDeclarationCount)
        assertEquals(2, kotlin.content.returnCount)
        assertEquals(1, kotlin.content.branchCount)
        assertEquals(1, kotlin.content.loopCount)
        assertEquals(1, kotlin.content.throwCount)
        assertEquals(1, kotlin.content.tryCount)
        assertTrue("nullableName" in kotlin.nullability.nullableTypeNames)
        assertTrue("input" in kotlin.nullability.nullableTypeNames)
        assertTrue("run" in kotlin.nullability.nullableTypeNames)
        assertEquals(0, kotlin.nullability.contradictoryNullabilityPatternCount)
        assertEquals(1, kotlin.nullability.nullComparisonCount)
        assertEquals(0, kotlin.nullability.nullabilityCastCount)
        assertEquals(0, kotlin.nullability.safeCallCount)
        assertEquals(1, kotlin.nullability.totalNullabilityOperationCount)
    }

    @Test
    fun `scans kotlin nullability operation and contradiction counts`() {
        val kotlin =
            SourceTextScanner().scanKotlin(
                """
                package com.example
                class App {
                  fun lookup(source: Any?): String? {
                    val first = source as String
                    if (first == null) return null
                    var second = source as String
                    if (second != null) second = second.trim()
                    val third = source as String?
                    return third?.trim()
                  }
                }
                """.trimIndent(),
            )

        assertEquals(2, kotlin.nullability.contradictoryNullabilityPatternCount)
        assertEquals(2, kotlin.nullability.nullComparisonCount)
        assertEquals(3, kotlin.nullability.nullabilityCastCount)
        assertEquals(1, kotlin.nullability.safeCallCount)
        assertEquals(6, kotlin.nullability.totalNullabilityOperationCount)
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
