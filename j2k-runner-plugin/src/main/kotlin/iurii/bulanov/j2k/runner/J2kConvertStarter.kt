@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner

import com.intellij.openapi.application.ApplicationStarter
import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.benchmark.conversion.ConversionPathResolver
import iurii.bulanov.benchmark.conversion.ConversionPaths
import iurii.bulanov.benchmark.conversion.ConversionReport
import iurii.bulanov.benchmark.conversion.ConversionReportWriter
import iurii.bulanov.benchmark.conversion.ConversionStatus
import iurii.bulanov.benchmark.conversion.GeneratedKotlinCollector
import iurii.bulanov.benchmark.conversion.SourceTreeStager
import iurii.bulanov.j2k.runner.engine.J2kConversionEngineFactory
import iurii.bulanov.j2k.runner.engine.J2kConversionRunner
import iurii.bulanov.j2k.runner.ide.RunnerProjectFactory
import iurii.bulanov.j2k.runner.ide.StagedPsiResolver
import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.source.SourceFileDiscovery
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Headless IntelliJ application starter that runs static J2K for a benchmark.
 *
 * The converter itself is supplied by a [iurii.bulanov.j2k.runner.engine.J2kConversionEngine]
 * selected through [J2kConversionEngineFactory]; this starter only orchestrates discovery,
 * staging, conversion, output collection, and reporting.
 */
class J2kConvertStarter : ApplicationStarter {
    private val configParser = BenchmarkConfigParser()
    private val pathResolver = ConversionPathResolver()
    private val sourceFileDiscovery = SourceFileDiscovery()
    private val sourceTreeStager = SourceTreeStager()
    private val generatedKotlinCollector = GeneratedKotlinCollector()
    private val reportWriter = ConversionReportWriter()
    private val engineFactory = J2kConversionEngineFactory()
    private val projectFactory = RunnerProjectFactory()
    private val psiResolver = StagedPsiResolver()

    /**
     * Command token used by the IntelliJ launcher.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override val commandName: String = "j2k-convert"

    /**
     * Runs conversion off the EDT because J2K asserts background-thread access.
     */
    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    /**
     * Allows the command to run in the GitHub Actions IDE process.
     */
    override val isHeadless: Boolean = true

    /**
     * Executes conversion and terminates the IntelliJ process.
     */
    override fun main(args: List<String>) {
        val command = args.toList()
        var config: BenchmarkConfig? = null
        var paths: ConversionPaths? = null
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var exitCode = 0

        try {
            val runnerArgs = if (args.firstOrNull() == "j2k-convert") args.drop(1) else args
            val options = J2kRunnerOptions.parse(runnerArgs)
            val harnessRoot = options.harnessRoot()
            config = configParser.parse(options.configPath)
            paths = pathResolver.resolve(config, options.toPathOverrides()).resolveAgainst(harnessRoot)
            paths.logsDirectory.createDirectories()
            val logger = ConversionLogger(paths)
            val conversionRunner = J2kConversionRunner(logger, projectFactory, psiResolver)

            logger.log(
                "conversion_started",
                mapOf(
                    "benchmark_id" to config.id,
                    "config_path" to options.configPath.toString(),
                    "idea_kotlin_plugin_use_k2" to System.getProperty("idea.kotlin.plugin.use.k2"),
                ),
            )
            val checkoutDirectory = harnessRoot.resolve(config.checkout.directory).normalize()
            val javaFiles = sourceFileDiscovery.discoverJavaFiles(checkoutDirectory, config.java.sourceRoots).files
            logger.log(
                "conversion_sources_discovered",
                mapOf(
                    "benchmark_id" to config.id,
                    "checkout_directory" to checkoutDirectory.toString(),
                    "source_java_file_count" to javaFiles.size,
                ),
            )

            val stagingResult = sourceTreeStager.stage(checkoutDirectory, paths.stagingDirectory)
            psiResolver.refreshStagedSourceTree(paths.stagingDirectory)
            logger.log(
                "conversion_sources_staged",
                mapOf(
                    "benchmark_id" to config.id,
                    "staging_directory" to paths.stagingDirectory.toString(),
                    "copied_file_count" to stagingResult.copiedFileCount,
                ),
            )
            val engine = engineFactory.create()
            val j2kResult = conversionRunner.run(engine, config, paths, javaFiles.size)
            warnings += j2kResult.warnings
            errors += j2kResult.errors
            logger.log(
                "conversion_j2k_completed",
                mapOf(
                    "benchmark_id" to config.id,
                    "converted_file_count" to j2kResult.convertedFiles.size,
                    "failed_file_count" to j2kResult.errors.size,
                ),
            )
            val collection =
                if (j2kResult.convertedFiles.isNotEmpty()) {
                    generatedKotlinCollector.writeConvertedFiles(paths.generatedKotlinDirectory, j2kResult.convertedFiles)
                } else {
                    warnings += "J2K returned no direct file map; collecting Kotlin files from staged source tree."
                    generatedKotlinCollector.collectFromStaging(
                        paths.stagingDirectory,
                        config.java.sourceRoots,
                        paths.generatedKotlinDirectory,
                    )
                }
            warnings += collection.warnings
            logger.log(
                "conversion_outputs_collected",
                mapOf(
                    "benchmark_id" to config.id,
                    "generated_kotlin_file_count" to collection.generatedFiles.size,
                    "warning_count" to warnings.size,
                ),
            )

            val status =
                when {
                    errors.isNotEmpty() -> ConversionStatus.PARTIAL
                    warnings.isNotEmpty() -> ConversionStatus.COMPLETED_WITH_WARNINGS
                    else -> ConversionStatus.COMPLETED
                }
            reportWriter.write(
                ConversionReport(
                    config = config,
                    status = status,
                    sourceJavaFileCount = javaFiles.size,
                    generatedKotlinFileCount = collection.generatedFiles.size,
                    paths = paths,
                    converterCommand = command,
                    warnings = warnings,
                    errors = errors,
                ),
            )
            logger.log(
                "conversion_completed",
                mapOf(
                    "benchmark_id" to config.id,
                    "source_java_file_count" to javaFiles.size,
                    "generated_kotlin_file_count" to collection.generatedFiles.size,
                    "status" to status.name.lowercase(),
                ),
            )
        } catch (exception: Throwable) {
            exitCode = 1
            errors += exception.describe()
            val safeConfig = config
            val safePaths = paths
            if (safeConfig != null && safePaths != null) {
                reportWriter.writeFailure(
                    ConversionReport(
                        config = safeConfig,
                        status = ConversionStatus.FAILED,
                        sourceJavaFileCount = 0,
                        generatedKotlinFileCount = 0,
                        paths = safePaths,
                        converterCommand = command,
                        warnings = warnings,
                        errors = errors,
                    ),
                )
                ConversionLogger(safePaths).log(
                    "conversion_failed",
                    mapOf(
                        "benchmark_id" to safeConfig.id,
                        "error" to errors.last(),
                        "stack_trace" to exception.stackTraceToString(),
                    ),
                )
            } else {
                println(JsonEncoder.encode(linkedMapOf("level" to "ERROR", "event" to "conversion_failed", "error" to errors.last())))
            }
        } finally {
            kotlin.system.exitProcess(exitCode)
        }
    }

    /**
     * Resolves conversion output paths against the repository root.
     */
    private fun ConversionPaths.resolveAgainst(harnessRoot: Path): ConversionPaths =
        ConversionPaths(
            stagingDirectory = harnessRoot.resolve(stagingDirectory).normalize(),
            generatedKotlinDirectory = harnessRoot.resolve(generatedKotlinDirectory).normalize(),
            conversionReport = harnessRoot.resolve(conversionReport).normalize(),
            logsDirectory = harnessRoot.resolve(logsDirectory).normalize(),
        )
}
