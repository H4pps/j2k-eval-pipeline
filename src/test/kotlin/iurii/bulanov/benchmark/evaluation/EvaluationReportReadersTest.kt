package iurii.bulanov.benchmark.evaluation

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvaluationReportReadersTest {
    @Test
    fun `reads checkout and conversion reports`() {
        val directory = Files.createTempDirectory("evaluation-report-readers-")
        val checkout = directory.resolve("checkout.json")
        val conversion = directory.resolve("conversion.json")
        checkout.writeText(
            """
            {
              "benchmark": {"id": "sample"},
              "build_status": "passed",
              "java_file_count": 7,
              "run_build": true
            }
            """.trimIndent(),
        )
        conversion.writeText(
            """
            {
              "benchmark": {"id": "sample"},
              "status": "partial",
              "counts": {
                "source_java_file_count": 7,
                "generated_kotlin_file_count": 6,
                "warning_count": 1,
                "error_count": 1
              },
              "warnings": ["batch failed"],
              "errors": ["Example.java: failed"]
            }
            """.trimIndent(),
        )

        val readers = EvaluationReportReaders()
        val checkoutEvaluation = readers.readCheckout(checkout)
        val conversionEvaluation = readers.readConversion(conversion)

        assertTrue(checkoutEvaluation.available)
        assertEquals("passed", checkoutEvaluation.buildStatus)
        assertEquals(7, checkoutEvaluation.javaFileCount)
        assertEquals(true, checkoutEvaluation.runBuild)
        assertEquals("sample", checkoutEvaluation.benchmarkId)
        assertTrue(conversionEvaluation.available)
        assertEquals("partial", conversionEvaluation.status)
        assertEquals(7, conversionEvaluation.sourceJavaFileCount)
        assertEquals(6, conversionEvaluation.generatedKotlinFileCount)
        assertEquals(1, conversionEvaluation.warningCount)
        assertEquals(1, conversionEvaluation.errorCount)
        assertEquals("sample", conversionEvaluation.benchmarkId)
    }

    @Test
    fun `missing reports become unavailable metadata`() {
        val missing = Files.createTempDirectory("evaluation-report-missing-").resolve("missing.json")
        val readers = EvaluationReportReaders()

        assertFalse(readers.readCheckout(missing).available)
        assertFalse(readers.readConversion(missing).available)
    }
}
