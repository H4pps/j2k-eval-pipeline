package iurii.bulanov.cli

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BenchmarkCheckoutCliTest {
    @Test
    fun `checkout benchmark help includes supported options`() {
        val result = J2kEvalCli().test("checkout-benchmark --help")

        assertEquals(0, result.statusCode)
        assertContains(result.output, "--config")
        assertContains(result.output, "--run-build")
        assertContains(result.output, "--github-summary")
        assertContains(result.output, "--github-output")
    }

    @Test
    fun `missing required config option fails with usage output`() {
        val result = J2kEvalCli().test("checkout-benchmark")

        assertEquals(1, result.statusCode)
        assertContains(result.output, "Usage:")
        assertContains(result.output, "--config")
    }

    @Test
    fun `legacy benchmark command fails`() {
        val legacyCommand = listOf("validate", "benchmark").joinToString("-")
        val result = J2kEvalCli().test("$legacyCommand --config benchmarks/hikaricp.yml")

        assertEquals(1, result.statusCode)
        assertContains(result.output.lowercase(), "no such")
    }
}
