package iurii.bulanov.benchmark.conversion

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

class GeneratedKotlinCollectorTest {
    @Test
    fun `collects generated kotlin files preserving source-root relative paths`() {
        val staging = Files.createTempDirectory("generated-kotlin-staging-")
        val generated = Files.createTempDirectory("generated-kotlin-output-")
        staging.resolve("src/main/java/com/example").createDirectories()
        staging.resolve("src/main/java/com/example/App.kt").writeText("package com.example\nclass App")

        val result = GeneratedKotlinCollector().collectFromStaging(staging, listOf("src/main/java"), generated)

        assertEquals(1, result.generatedFiles.size)
        assertTrue(generated.resolve("com/example/App.kt").exists())
    }

    @Test
    fun `writes converted files returned from j2k map`() {
        val generated = Files.createTempDirectory("generated-kotlin-direct-")

        val result =
            GeneratedKotlinCollector().writeConvertedFiles(
                generated,
                mapOf(Path.of("com/example/App.java") to "package com.example\nclass App"),
            )

        assertEquals(1, result.generatedFiles.size)
        assertEquals("package com.example\nclass App", generated.resolve("com/example/App.kt").readText())
    }

    @Test
    fun `missing staged source root becomes warning`() {
        val staging = Files.createTempDirectory("generated-kotlin-missing-root-")
        val generated = Files.createTempDirectory("generated-kotlin-missing-output-")

        val result = GeneratedKotlinCollector().collectFromStaging(staging, listOf("src/main/java"), generated)

        assertEquals(0, result.generatedFiles.size)
        assertContains(result.warnings.single(), "staged source root missing")
    }
}
