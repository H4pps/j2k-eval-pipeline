package iurii.bulanov.logging

import java.time.Instant

/**
 * Emits structured JSON log lines for benchmark checkout events.
 *
 * [now] and [writeLine] are injectable for deterministic tests while keeping
 * production defaults bound to current UTC time and stdout.
 */
class JsonLineLogger(
    private val now: () -> Instant = Instant::now,
    private val writeLine: (String) -> Unit = ::println
) : StructuredLogger {
    /**
     * Writes an info-level JSON log line with an event name and optional fields.
     */
    override fun info(event: String, fields: Map<String, Any?>) {
        log("INFO", event, fields)
    }

    /**
     * Writes an error-level JSON log line with an event name and optional fields.
     */
    override fun error(event: String, fields: Map<String, Any?>) {
        log("ERROR", event, fields)
    }

    /**
     * Writes a JSON log line to stdout.
     */
    private fun log(level: String, event: String, fields: Map<String, Any?>) {
        val payload = linkedMapOf<String, Any?>(
            "timestamp" to now().toString(),
            "level" to level,
            "event" to event
        )
        payload.putAll(fields)
        writeLine(JsonEncoder.encode(payload))
    }
}

/**
 * Encodes primitive Kotlin values, maps, and lists to JSON strings.
 */
object JsonEncoder {
    /**
     * Serializes a Kotlin value into JSON.
     */
    fun encode(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> "\"" + escape(value) + "\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> ->
                value.entries.joinToString(prefix = "{", postfix = "}") { entry ->
                    val key = entry.key?.toString() ?: "null"
                    "${encode(key)}:${encode(entry.value)}"
                }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { item -> encode(item) }
            else -> encode(value.toString())
        }

    /**
     * Escapes JSON control characters in a string.
     */
    private fun escape(value: String): String {
        val result = StringBuilder(value.length + 8)
        value.forEach { character ->
            when (character) {
                '\\' -> result.append("\\\\")
                '"' -> result.append("\\\"")
                '\b' -> result.append("\\b")
                '\u000C' -> result.append("\\f")
                '\n' -> result.append("\\n")
                '\r' -> result.append("\\r")
                '\t' -> result.append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        result.append("\\u%04x".format(character.code))
                    } else {
                        result.append(character)
                    }
                }
            }
        }
        return result.toString()
    }
}
