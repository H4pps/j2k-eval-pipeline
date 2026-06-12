package iurii.bulanov.j2k.runner

// Shared low-level helpers used across the headless J2K runner components.

/**
 * Produces a compact error string with the root cause when available.
 */
internal fun Throwable.describe(): String {
    val rootCause = generateSequence(this) { it.cause }.last()
    val rootMessage = rootCause.message ?: rootCause::class.java.name
    return if (rootCause === this) {
        rootMessage
    } else {
        "${this::class.java.name}: $rootMessage"
    }
}

/**
 * Rethrows JVM-level fatal failures while keeping converter assertions reportable.
 */
internal fun Throwable.rethrowIfFatal() {
    if (this is ThreadDeath || this is VirtualMachineError || this is InterruptedException) {
        throw this
    }
}

/**
 * Normalizes line endings before comparing PSI text with on-disk source text.
 */
internal fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace("\r", "\n")
