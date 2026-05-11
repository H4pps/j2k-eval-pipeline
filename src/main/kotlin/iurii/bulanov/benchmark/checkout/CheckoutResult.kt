package iurii.bulanov.benchmark.checkout

import iurii.bulanov.benchmark.config.BenchmarkConfig

/**
 * Checkout output returned by benchmark checkout sanity checks.
 */
data class CheckoutResult(
    val config: BenchmarkConfig,
    val javaFileCount: Int,
    val buildStatus: BuildStatus,
)

/**
 * Build status emitted to reports and GitHub outputs.
 */
enum class BuildStatus {
    PASSED,
    FAILED,
    SKIPPED,
}
