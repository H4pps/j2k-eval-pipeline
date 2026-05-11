package iurii.bulanov.benchmark.checkout

/**
 * Signals an invalid benchmark configuration or checkout sanity-check failure.
 */
class CheckoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
