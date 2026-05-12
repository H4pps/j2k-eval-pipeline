package iurii.bulanov.benchmark.conversion

/**
 * Failure raised by the benchmark conversion infrastructure.
 */
class ConversionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
