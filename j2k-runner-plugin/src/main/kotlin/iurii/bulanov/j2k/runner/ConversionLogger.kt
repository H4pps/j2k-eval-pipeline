package iurii.bulanov.j2k.runner

import iurii.bulanov.benchmark.conversion.ConversionPaths
import iurii.bulanov.logging.JsonEncoder
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Emits structured runner log lines to stdout and the per-run runner log file.
 *
 * Bound to a resolved [ConversionPaths] so every component sharing the instance appends to the
 * same `logs/j2k-runner.log`.
 */
class ConversionLogger(
    private val paths: ConversionPaths,
) {
    /**
     * Emits a structured INFO log line to stdout and the runner log file.
     */
    fun log(
        event: String,
        fields: Map<String, Any?>,
    ) {
        val payload = JsonEncoder.encode(linkedMapOf("level" to "INFO", "event" to event) + fields)
        println(payload)
        val logPath = paths.logsDirectory.resolve("j2k-runner.log")
        if (!Files.exists(logPath)) {
            logPath.parent.createDirectories()
            logPath.writeText("")
        }
        Files.writeString(logPath, payload + "\n", StandardOpenOption.APPEND)
    }
}
