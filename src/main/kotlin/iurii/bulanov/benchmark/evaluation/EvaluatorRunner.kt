package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger
import iurii.bulanov.source.DiscoveredSourceFile
import iurii.bulanov.source.SourceFileDiscovery
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

/**
 * Runs Phase 2 evaluation independently from CLI parsing.
 */
class EvaluatorRunner(
    private val logger: StructuredLogger = JsonLineLogger(),
    private val configParser: BenchmarkConfigParser = BenchmarkConfigParser(),
    private val sourceFileDiscovery: SourceFileDiscovery = SourceFileDiscovery(),
    private val reportWriter: EvaluationReportWriter = EvaluationReportWriter(logger = logger),
) {
    /**
     * Executes evaluator discovery and report generation for [request].
     */
    fun run(request: EvaluationRequest): Int {
        logger.info("evaluation_started", mapOf("config_path" to request.configPath.toString()))
        return try {
            val config = configParser.parse(request.configPath)
            val checkoutDirectory = Paths.get(config.checkout.directory).normalize()
            val generatedKotlinDirectory = resolveGeneratedKotlinDirectory(config.id, request.generatedKotlinDirectory)
            val reportDirectory = resolveReportDirectory(config.id, request.reportDirectory)

            val javaFiles = sourceFileDiscovery.discoverJavaFiles(checkoutDirectory, config.java.sourceRoots).files
            val kotlinDiscovery = sourceFileDiscovery.discoverKotlinFiles(generatedKotlinDirectory)
            val warnings =
                buildList {
                    if (!kotlinDiscovery.directoryExists) {
                        add(
                            EvaluationWarning(
                                code = "generated_kotlin_directory_missing",
                                message = "Generated Kotlin directory does not exist yet; converter output is expected in a later phase.",
                                path = generatedKotlinDirectory.toString(),
                            ),
                        )
                    } else {
                        addAll(
                            coverageWarnings(
                                javaFiles = javaFiles,
                                kotlinFiles = kotlinDiscovery.files,
                                sourceRoots = config.java.sourceRoots,
                            ),
                        )
                    }
                }
            val result =
                EvaluationResult(
                    config = config,
                    checkoutDirectory = checkoutDirectory,
                    generatedKotlinDirectory = generatedKotlinDirectory,
                    reportDirectory = reportDirectory,
                    javaFiles = javaFiles,
                    kotlinFiles = kotlinDiscovery.files,
                    status = if (warnings.isEmpty()) EvaluationStatus.COMPLETED else EvaluationStatus.COMPLETED_WITH_WARNINGS,
                    warnings = warnings,
                )

            reportWriter.write(result)
            logger.info(
                "evaluation_completed",
                mapOf(
                    "id" to config.id,
                    "java_file_count" to result.javaFiles.size,
                    "kotlin_file_count" to result.kotlinFiles.size,
                    "warning_count" to result.warnings.size,
                    "status" to result.status.name.lowercase(),
                ),
            )
            0
        } catch (exception: Exception) {
            logger.error("evaluation_failed", mapOf("error" to (exception.message ?: exception::class.simpleName)))
            1
        }
    }

    /**
     * Resolves the generated Kotlin output directory using Phase 2 defaults.
     */
    private fun resolveGeneratedKotlinDirectory(
        benchmarkId: String,
        configuredPath: Path?,
    ): Path = (configuredPath ?: Path.of("build", "j2k", benchmarkId, "generated-kotlin")).normalize()

    /**
     * Resolves the evaluator report directory using Phase 2 defaults.
     */
    private fun resolveReportDirectory(
        benchmarkId: String,
        configuredPath: Path?,
    ): Path = (configuredPath ?: Path.of("build", "reports", "j2k-eval", benchmarkId)).normalize()

    /**
     * Compares Java inputs with generated Kotlin outputs using the converter output layout.
     */
    private fun coverageWarnings(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        sourceRoots: List<String>,
    ): List<EvaluationWarning> {
        val expectedKotlinPaths =
            javaFiles
                .map { expectedKotlinRelativePath(it.relativePath, sourceRoots) }
                .toSet()
        val actualKotlinPaths = kotlinFiles.map { it.relativePath.normalize() }.toSet()
        val missingKotlinPaths = expectedKotlinPaths.minus(actualKotlinPaths).sortedBy { it.toString() }
        val unexpectedKotlinPaths = actualKotlinPaths.minus(expectedKotlinPaths).sortedBy { it.toString() }

        val missingWarnings =
            missingKotlinPaths.map { path ->
                EvaluationWarning(
                    code = "generated_kotlin_file_missing",
                    message = "No generated Kotlin file matched a configured Java input.",
                    path = path.toString(),
                )
            }
        val unexpectedWarnings =
            unexpectedKotlinPaths.map { path ->
                EvaluationWarning(
                    code = "generated_kotlin_file_unexpected",
                    message = "Generated Kotlin file does not match any configured Java input.",
                    path = path.toString(),
                )
            }
        return missingWarnings + unexpectedWarnings
    }

    /**
     * Maps a checkout-relative Java path to the generated-output-relative Kotlin path.
     */
    private fun expectedKotlinRelativePath(
        javaRelativePath: Path,
        sourceRoots: List<String>,
    ): Path {
        val normalizedJavaPath = javaRelativePath.normalize()
        val sourceRootRelativePath =
            sourceRoots
                .map { Path.of(it).normalize() }
                .firstOrNull { normalizedJavaPath.startsWith(it) }
                ?.relativize(normalizedJavaPath)
                ?: normalizedJavaPath
        val kotlinFileName = "${sourceRootRelativePath.nameWithoutExtension}.kt"
        return sourceRootRelativePath.parent?.resolve(kotlinFileName)?.normalize() ?: Path.of(kotlinFileName)
    }
}
