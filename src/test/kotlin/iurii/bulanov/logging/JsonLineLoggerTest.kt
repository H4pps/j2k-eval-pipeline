package iurii.bulanov.logging

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonLineLoggerTest {
    @Test
    fun `info writes deterministic json line with fields`() {
        val lines = mutableListOf<String>()
        val logger =
            JsonLineLogger(
                now = { Instant.parse("2026-05-11T07:15:30Z") },
                writeLine = lines::add
            )

        logger.info(
            event = "benchmark_checkout_started",
            fields = linkedMapOf("config_path" to "benchmarks/hikaricp.yml", "run_build" to false)
        )

        assertEquals(
            """{"timestamp":"2026-05-11T07:15:30Z", "level":"INFO", "event":"benchmark_checkout_started", "config_path":"benchmarks/hikaricp.yml", "run_build":false}""",
            lines.single()
        )
    }

    @Test
    fun `error writes deterministic json line without extra fields`() {
        val lines = mutableListOf<String>()
        val logger =
            JsonLineLogger(
                now = { Instant.parse("2026-05-11T07:15:31Z") },
                writeLine = lines::add
            )

        logger.error("benchmark_checkout_failed")

        assertEquals(
            """{"timestamp":"2026-05-11T07:15:31Z", "level":"ERROR", "event":"benchmark_checkout_failed"}""",
            lines.single()
        )
    }
}
