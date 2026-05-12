package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import iurii.bulanov.source.SourceFileDiscovery
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Runs evaluator metric calculation independently from CLI parsing.
 */
class EvaluatorRunner(
    private val logger: StructuredLogger = JsonLineLogger(),
    private val configParser: BenchmarkConfigParser = BenchmarkConfigParser(),
    private val sourceFileDiscovery: SourceFileDiscovery = SourceFileDiscovery(),
    private val reportReaders: EvaluationReportReaders = EvaluationReportReaders(),
    private val metricsCalculator: EvaluationMetricsCalculator = EvaluationMetricsCalculator(),
    private val reportWriter: EvaluationReportWriter = EvaluationReportWriter(logger = logger),
) {
    /**
     * Executes evaluator discovery and report generation for [request].
     */
    fun run(request: EvaluationRequest): Int {
        logger.info("evaluation_started", mapOf("config_path" to request.configPath.toString()))
        return try {
            val result = evaluate(request)
            reportWriter.write(result)
            logEvaluationCompleted(result)
            0
        } catch (exception: Exception) {
            logger.error("evaluation_failed", mapOf("error" to (exception.message ?: exception::class.simpleName)))
            1
        }
    }

    /**
     * Builds the complete evaluator result before report writing.
     */
    private fun evaluate(request: EvaluationRequest): EvaluationResult {
        val config = configParser.parse(request.configPath)
        val paths = EvaluationPaths.from(config.id, config.checkout.directory, request)
        val javaFiles = sourceFileDiscovery.discoverJavaFiles(paths.checkoutDirectory, config.java.sourceRoots).files
        val kotlinDiscovery = sourceFileDiscovery.discoverKotlinFiles(paths.generatedKotlinDirectory)
        val checkout = reportReaders.readCheckout(paths.checkoutReport)
        val conversion = reportReaders.readConversion(paths.conversionReport)
        val metrics = metricsCalculator.calculate(javaFiles, kotlinDiscovery.files, config.java.sourceRoots)
        val warnings = warnings(config.id, paths, kotlinDiscovery.directoryExists, checkout, conversion, metrics)
        return EvaluationResult(
            config = config,
            checkoutDirectory = paths.checkoutDirectory,
            generatedKotlinDirectory = paths.generatedKotlinDirectory,
            reportDirectory = paths.reportDirectory,
            conversionReportPath = paths.conversionReport,
            checkoutReportPath = paths.checkoutReport,
            javaFiles = javaFiles,
            kotlinFiles = kotlinDiscovery.files,
            checkout = checkout,
            conversion = conversion,
            fileCoverage = metrics.fileCoverage,
            structure = metrics.structure,
            quality = metrics.quality,
            status = if (warnings.isEmpty()) EvaluationStatus.COMPLETED else EvaluationStatus.COMPLETED_WITH_WARNINGS,
            warnings = warnings,
        )
    }

    /**
     * Builds all evaluator warnings from missing metadata, conversion failures, and metrics.
     */
    private fun warnings(
        benchmarkId: String,
        paths: EvaluationPaths,
        generatedKotlinDirectoryExists: Boolean,
        checkout: CheckoutEvaluation,
        conversion: ConversionEvaluation,
        metrics: EvaluationMetrics,
    ): List<EvaluationWarning> =
        buildList {
            addMissingGeneratedDirectoryWarning(generatedKotlinDirectoryExists, paths.generatedKotlinDirectory)
            addReportAvailabilityWarnings(checkout, paths.checkoutReport, conversion, paths.conversionReport)
            addReportIdentityWarnings(benchmarkId, checkout, paths.checkoutReport, conversion, paths.conversionReport)
            addConversionWarnings(conversion)
            addFileCoverageWarnings(metrics.fileCoverage)
            addAll(metrics.quality.findings)
        }

    /**
     * Adds a warning when generated Kotlin output is not present.
     */
    private fun MutableList<EvaluationWarning>.addMissingGeneratedDirectoryWarning(
        directoryExists: Boolean,
        generatedKotlinDirectory: Path,
    ) {
        if (!directoryExists) {
            add(
                EvaluationWarning(
                    code = "generated_kotlin_directory_missing",
                    message =
                        "Generated Kotlin directory does not exist; run static J2K conversion before interpreting coverage.",
                    path = generatedKotlinDirectory.toString(),
                ),
            )
        }
    }

    /**
     * Writes a structured completion log event.
     */
    private fun logEvaluationCompleted(result: EvaluationResult) {
        logger.info(
            "evaluation_completed",
            mapOf(
                "id" to result.config.id,
                "java_file_count" to result.javaFiles.size,
                "kotlin_file_count" to result.kotlinFiles.size,
                "warning_count" to result.warnings.size,
                "conversion_status" to result.conversion.status,
                "status" to result.status.name.lowercase(),
            ),
        )
    }

    /**
     * Adds warnings for missing upstream reports.
     */
    private fun MutableList<EvaluationWarning>.addReportAvailabilityWarnings(
        checkout: CheckoutEvaluation,
        checkoutReportPath: Path,
        conversion: ConversionEvaluation,
        conversionReportPath: Path,
    ) {
        if (!checkout.available) {
            add(
                EvaluationWarning(
                    code = "checkout_report_missing",
                    message = "Checkout metadata report is unavailable; original build status cannot be included.",
                    path = checkoutReportPath.toString(),
                ),
            )
        }
        if (!conversion.available) {
            add(
                EvaluationWarning(
                    code = "conversion_report_missing",
                    message = "Conversion metadata report is unavailable; J2K execution status cannot be included.",
                    path = conversionReportPath.toString(),
                ),
            )
        }
        if (checkout.buildStatus == "failed") {
            add(EvaluationWarning(code = "original_build_failed", message = "Original benchmark build failed during checkout validation."))
        }
    }

    /**
     * Adds warnings when metadata reports belong to another benchmark id.
     */
    private fun MutableList<EvaluationWarning>.addReportIdentityWarnings(
        benchmarkId: String,
        checkout: CheckoutEvaluation,
        checkoutReportPath: Path,
        conversion: ConversionEvaluation,
        conversionReportPath: Path,
    ) {
        if (checkout.available && checkout.benchmarkId != null && checkout.benchmarkId != benchmarkId) {
            add(
                EvaluationWarning(
                    code = "checkout_report_benchmark_mismatch",
                    message = "Checkout metadata report benchmark id does not match the active benchmark config.",
                    path = checkoutReportPath.toString(),
                ),
            )
        }
        if (conversion.available && conversion.benchmarkId != null && conversion.benchmarkId != benchmarkId) {
            add(
                EvaluationWarning(
                    code = "conversion_report_benchmark_mismatch",
                    message = "Conversion metadata report benchmark id does not match the active benchmark config.",
                    path = conversionReportPath.toString(),
                ),
            )
        }
    }

    /**
     * Adds warnings for conversion warnings and source-level converter failures.
     */
    private fun MutableList<EvaluationWarning>.addConversionWarnings(conversion: ConversionEvaluation) {
        if (conversion.warningCount > 0) {
            add(
                EvaluationWarning(
                    code = "conversion_warning",
                    message = "J2K conversion reported warnings.",
                    count = conversion.warningCount,
                ),
            )
        }
        if (conversion.errorCount > 0 || conversion.status in setOf("partial", "failed")) {
            add(
                EvaluationWarning(
                    code = "conversion_failure",
                    message = "J2K conversion reported source-level failures.",
                    count = conversion.errorCount.takeIf { it > 0 },
                ),
            )
        }
    }

    /**
     * Adds warnings for file coverage and package preservation problems.
     */
    private fun MutableList<EvaluationWarning>.addFileCoverageWarnings(fileCoverage: FileCoverageMetrics) {
        fileCoverage.missingKotlinFiles.forEach { path ->
            add(
                EvaluationWarning(
                    code = "generated_kotlin_file_missing",
                    message = "No generated Kotlin file matched a configured Java input.",
                    path = path,
                ),
            )
        }
        fileCoverage.unexpectedKotlinFiles.forEach { path ->
            add(
                EvaluationWarning(
                    code = "generated_kotlin_file_unexpected",
                    message = "Generated Kotlin file does not match any configured Java input.",
                    path = path,
                ),
            )
        }
        fileCoverage.emptyGeneratedFiles.forEach { path ->
            add(EvaluationWarning(code = "generated_kotlin_file_empty", message = "Generated Kotlin file is empty.", path = path))
        }
        fileCoverage.packageMismatchFiles.forEach { path ->
            add(
                EvaluationWarning(
                    code = "package_preservation_mismatch",
                    message = "Generated Kotlin package declaration or path does not match the Java input.",
                    path = path,
                ),
            )
        }
    }
}

/**
 * Resolved evaluator input and output paths.
 */
private data class EvaluationPaths(
    val checkoutDirectory: Path,
    val generatedKotlinDirectory: Path,
    val reportDirectory: Path,
    val conversionReport: Path,
    val checkoutReport: Path,
) {
    companion object {
        /**
         * Resolves evaluator paths from a benchmark id and request overrides.
         */
        fun from(
            benchmarkId: String,
            checkoutDirectory: String,
            request: EvaluationRequest,
        ): EvaluationPaths =
            EvaluationPaths(
                checkoutDirectory = Paths.get(checkoutDirectory).normalize(),
                generatedKotlinDirectory = request.generatedKotlinDirectory ?: Path.of("build", "j2k", benchmarkId, "generated-kotlin"),
                reportDirectory = request.reportDirectory ?: Path.of("build", "reports", "j2k-eval", benchmarkId),
                conversionReport = request.conversionReport ?: Path.of("build", "j2k", benchmarkId, "conversion.json"),
                checkoutReport = request.checkoutReport ?: Path.of("build", "benchmarks", benchmarkId, "checkout.json"),
            ).normalize()
    }

    /**
     * Normalizes all resolved paths.
     */
    private fun normalize(): EvaluationPaths =
        copy(
            generatedKotlinDirectory = generatedKotlinDirectory.normalize(),
            reportDirectory = reportDirectory.normalize(),
            conversionReport = conversionReport.normalize(),
            checkoutReport = checkoutReport.normalize(),
        )
}
