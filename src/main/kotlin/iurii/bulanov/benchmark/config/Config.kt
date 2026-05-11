package iurii.bulanov.benchmark.config

/**
 * Parsed benchmark configuration used by the checkout CLI.
 */
data class BenchmarkConfig(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val repository: RepositoryConfig,
    val checkout: CheckoutConfig,
    val java: JavaConfig,
    val build: BuildConfig
)

/**
 * Repository metadata for benchmark checkout.
 */
data class RepositoryConfig(
    val upstream: String,
    val source: String,
    val ref: String,
    val branch: String
)

/**
 * Checkout location for a benchmark source tree.
 */
data class CheckoutConfig(
    val directory: String
)

/**
 * Java source layout information for checkout sanity checks.
 */
data class JavaConfig(
    val sourceRoots: List<String>
)

/**
 * Build command configuration for non-blocking benchmark build checks.
 */
data class BuildConfig(
    val tool: String,
    val workingDirectory: String,
    val commands: List<String>
)
