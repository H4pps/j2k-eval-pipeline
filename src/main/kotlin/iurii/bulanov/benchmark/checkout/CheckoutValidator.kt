package iurii.bulanov.benchmark.checkout

import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.CheckoutDirectoryPolicy
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import iurii.bulanov.process.CommandResult
import iurii.bulanov.process.ProcessExecutor
import iurii.bulanov.source.SourceFileDiscovery
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.streams.asSequence

/**
 * Runs benchmark checkout sanity checks by cloning, pin verification, source checks, and optional build.
 *
 * Structured logging is emitted through an injected [logger].
 */
class CheckoutValidator(
    private val processExecutor: ProcessExecutor = ProcessExecutor(),
    private val sourceFileDiscovery: SourceFileDiscovery = SourceFileDiscovery(),
    private val logger: StructuredLogger = JsonLineLogger(),
) {
    /**
     * Checks benchmark source inputs and optionally runs configured build commands.
     */
    fun validate(
        config: BenchmarkConfig,
        runBuild: Boolean,
    ): CheckoutResult {
        val checkoutDirectory = Paths.get(config.checkout.directory).normalize()
        recreateCheckoutDirectory(checkoutDirectory)

        runRequired(
            processExecutor.run(listOf("git", "clone", config.repository.source, checkoutDirectory.toString())),
            "failed to clone benchmark repository",
        )
        runRequired(
            processExecutor.run(listOf("git", "-C", checkoutDirectory.toString(), "checkout", "--force", config.repository.ref)),
            "failed to checkout pinned benchmark ref",
        )

        val headResult = processExecutor.run(listOf("git", "-C", checkoutDirectory.toString(), "rev-parse", "HEAD"))
        runRequired(headResult, "failed to resolve checked out commit")
        val actualRef = headResult.output.trim()
        if (actualRef != config.repository.ref) {
            throw CheckoutException("pinned ref verification failed: expected ${config.repository.ref}, got $actualRef")
        }
        logger.info(
            event = "benchmark_checkout_verified",
            fields =
                mapOf(
                    "checkout_dir" to checkoutDirectory.toString(),
                    "repo_source" to config.repository.source,
                    "repo_ref" to config.repository.ref,
                ),
        )

        val javaFileCount = sourceFileDiscovery.countJavaFiles(checkoutDirectory, config.java.sourceRoots)
        logger.info(
            event = "java_sources_validated",
            fields =
                mapOf(
                    "checkout_dir" to checkoutDirectory.toString(),
                    "java_roots" to config.java.sourceRoots,
                    "java_file_count" to javaFileCount,
                ),
        )

        val buildStatus =
            if (runBuild) {
                runBuildCommands(config, checkoutDirectory)
            } else {
                logger.info("benchmark_build_skipped", mapOf("reason" to "run-build flag not provided"))
                BuildStatus.SKIPPED
            }

        return CheckoutResult(config = config, javaFileCount = javaFileCount, buildStatus = buildStatus)
    }

    /**
     * Removes any previous checkout directory and creates a clean target directory.
     */
    private fun recreateCheckoutDirectory(checkoutDirectory: Path) {
        CheckoutDirectoryPolicy.validate(checkoutDirectory.toString().replace('\\', '/'))
        if (checkoutDirectory.exists()) {
            Files.walk(checkoutDirectory).use { stream ->
                stream
                    .asSequence()
                    .sortedByDescending { it.nameCount }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
        checkoutDirectory.parent?.createDirectories()
    }

    /**
     * Runs benchmark build commands and returns pass/fail status without throwing.
     */
    private fun runBuildCommands(
        config: BenchmarkConfig,
        checkoutDirectory: Path,
    ): BuildStatus {
        return try {
            val buildWorkingDirectory = resolveBuildWorkingDirectory(checkoutDirectory, config.build.workingDirectory)
            if (!buildWorkingDirectory.isDirectory()) {
                logger.error(
                    "benchmark_build_failed",
                    mapOf(
                        "reason" to "working directory does not exist",
                        "working_directory" to buildWorkingDirectory.toString(),
                    ),
                )
                return BuildStatus.FAILED
            }

            config.build.commands.forEach { command ->
                logger.info(
                    "benchmark_build_command_start",
                    mapOf("cwd" to buildWorkingDirectory.toString(), "command" to command),
                )
                val result = processExecutor.runShell(command, buildWorkingDirectory)
                logger.info(
                    "benchmark_build_command_finish",
                    mapOf("cwd" to buildWorkingDirectory.toString(), "command" to command, "exit_code" to result.exitCode),
                )
                if (result.exitCode != 0) {
                    logger.error(
                        "benchmark_build_failed",
                        mapOf("command" to command, "exit_code" to result.exitCode, "output" to result.output),
                    )
                    return BuildStatus.FAILED
                }
            }
            BuildStatus.PASSED
        } catch (exception: CheckoutException) {
            logger.error("benchmark_build_failed", mapOf("error" to exception.message))
            BuildStatus.FAILED
        }
    }

    /**
     * Resolves and validates the configured build working directory under the checkout root.
     */
    private fun resolveBuildWorkingDirectory(
        checkoutDirectory: Path,
        configuredPath: String,
    ): Path {
        val relativePath = Paths.get(configuredPath)
        if (relativePath.isAbsolute) {
            throw CheckoutException("build.workingDirectory must be a relative path: $configuredPath")
        }
        val resolved = checkoutDirectory.resolve(relativePath).normalize()
        if (!resolved.startsWith(checkoutDirectory.normalize())) {
            throw CheckoutException("build.workingDirectory escapes checkout directory: $configuredPath")
        }
        return resolved
    }

    /**
     * Throws a validation error when a required process command fails.
     */
    private fun runRequired(
        result: CommandResult,
        failureMessage: String,
    ) {
        if (result.exitCode != 0) {
            throw CheckoutException("$failureMessage (exit=${result.exitCode}): ${result.output}")
        }
    }
}
