package iurii.bulanov.logging

/**
 * Defines structured logging operations for info and error events.
 */
interface StructuredLogger {
    /**
     * Emits an info-level event with optional structured fields.
     */
    fun info(event: String, fields: Map<String, Any?> = emptyMap())

    /**
     * Emits an error-level event with optional structured fields.
     */
    fun error(event: String, fields: Map<String, Any?> = emptyMap())
}
