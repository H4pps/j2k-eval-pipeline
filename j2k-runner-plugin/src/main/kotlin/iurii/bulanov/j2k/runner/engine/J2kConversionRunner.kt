@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.engine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
import com.intellij.util.indexing.UnindexedFilesScanner
import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.conversion.ConversionPaths
import iurii.bulanov.j2k.runner.ConversionLogger
import iurii.bulanov.j2k.runner.describe
import iurii.bulanov.j2k.runner.ide.RunnerProjectFactory
import iurii.bulanov.j2k.runner.ide.StagedPsiResolver
import iurii.bulanov.j2k.runner.rethrowIfFatal
import java.nio.file.Path

/**
 * Drives one conversion run: disposable project lifecycle, PSI resolution, and the batch
 * conversion with a per-file fallback. The converter itself is supplied as a [J2kConversionEngine],
 * so this orchestration is identical for every engine.
 *
 * Smart mode is not a stable state: on cold machines indexing happens in waves, and a new
 * dumb-mode session can start mid-conversion. For smart-mode engines the runner therefore waits
 * for a *settled* smart window before converting and retries on [IndexNotReadyException]
 * (once for the batch, once per file in the fallback) instead of failing the run.
 */
class J2kConversionRunner(
    private val logger: ConversionLogger,
    private val projectFactory: RunnerProjectFactory = RunnerProjectFactory(),
    private val psiResolver: StagedPsiResolver = StagedPsiResolver(),
) {
    /**
     * Runs [engine] against the staged source tree and returns source-root-relative Kotlin code.
     */
    fun run(
        engine: J2kConversionEngine,
        config: BenchmarkConfig,
        paths: ConversionPaths,
        sourceJavaFileCount: Int,
    ): J2kRunResult {
        val project = projectFactory.createProject("j2k-${config.id}", paths.stagingDirectory, index = engine.requiresSmartMode)
        return try {
            logger.log("conversion_project_created", mapOf("benchmark_id" to config.id, "project_name" to project.name))
            logger.log(
                "conversion_inferred_annotations_configured",
                mapOf("benchmark_id" to config.id) + engine.configureProject(project),
            )
            val module = projectFactory.createModule(project, paths.stagingDirectory, config.java.sourceRoots)
            logger.log("conversion_module_created", mapOf("benchmark_id" to config.id, "module_name" to module.name))
            val dumbService = DumbService.getInstance(project)
            if (engine.requiresSmartMode) {
                indexProject(project, dumbService, config.id)
            }
            val javaFiles =
                ApplicationManager.getApplication().runReadAction(
                    Computable {
                        psiResolver
                            .findPsiJavaFiles(project, paths.stagingDirectory, config.java.sourceRoots)
                            .also(psiResolver::verifyPsiMatchesDisk)
                    },
                )
            require(javaFiles.size == sourceJavaFileCount) {
                "staged Java file count mismatch: expected $sourceJavaFileCount, found ${javaFiles.size}"
            }
            logger.log(
                "conversion_psi_files_resolved",
                mapOf("benchmark_id" to config.id, "psi_java_file_count" to javaFiles.size),
            )

            val relativePaths =
                javaFiles.associateWith { javaFile ->
                    psiResolver.relativeSourcePath(paths.stagingDirectory, config.java.sourceRoots, Path.of(javaFile.virtualFile.path))
                }
            val batchResult = convertBatchWithFallback(engine, project, module, dumbService, javaFiles, relativePaths, config)
            ApplicationManager.getApplication().invokeAndWait {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
            batchResult
        } finally {
            projectFactory.closeProject(project)
        }
    }

    /**
     * Converts all files as one batch, retrying once after re-settling smart mode when an indexing
     * wave interrupts a smart-mode engine; any other (or repeated) failure falls back per file.
     */
    private fun convertBatchWithFallback(
        engine: J2kConversionEngine,
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
        relativePaths: Map<PsiJavaFile, Path>,
        config: BenchmarkConfig,
    ): J2kRunResult {
        var retriesLeft = MAX_BATCH_RETRIES
        while (true) {
            try {
                val results = engine.convert(project, module, dumbService, javaFiles)
                require(results.size == javaFiles.size) {
                    "J2K result count mismatch: expected ${javaFiles.size}, found ${results.size}"
                }
                return J2kRunResult(
                    convertedFiles =
                        javaFiles
                            .zip(results)
                            .associate { (javaFile, kotlinCode) -> relativePaths.getValue(javaFile) to kotlinCode },
                    warnings = emptyList(),
                    errors = emptyList(),
                )
            } catch (exception: Throwable) {
                exception.rethrowIfFatal()
                if (engine.requiresSmartMode && exception.isIndexNotReady() && retriesLeft > 0) {
                    retriesLeft -= 1
                    logger.log(
                        "conversion_smart_mode_lost",
                        mapOf("benchmark_id" to config.id, "phase" to "batch", "error" to exception.describe()),
                    )
                    if (settleSafely(dumbService, config.id)) continue
                }
                logger.log(
                    "conversion_j2k_batch_failed",
                    mapOf(
                        "benchmark_id" to config.id,
                        "error" to exception.describe(),
                        "stack_trace" to exception.stackTraceToString(),
                    ),
                )
                return convertFilesIndividually(
                    engine = engine,
                    project = project,
                    module = module,
                    dumbService = dumbService,
                    javaFiles = javaFiles,
                    relativePaths = relativePaths,
                    config = config,
                    batchFailure = exception,
                )
            }
        }
    }

    /**
     * Triggers an unindexed-files scan and waits, with a hard time bound, for the project to reach
     * a settled smart mode — required before the smart-mode converters resolve through the
     * Analysis API. Safe to block here because the runner executes off the EDT.
     */
    private fun indexProject(
        project: Project,
        dumbService: DumbService,
        benchmarkId: String,
    ) {
        UnindexedFilesScanner(project, "j2k-runner conversion").queue()
        val startMs = System.currentTimeMillis()
        waitUntilSmartAndSettled(dumbService)
        logger.log(
            "conversion_indexing_completed",
            mapOf(
                "benchmark_id" to benchmarkId,
                "reached_smart_mode" to true,
                "elapsed_ms" to (System.currentTimeMillis() - startMs),
            ),
        )
    }

    /**
     * Waits until the project is in smart mode and *stays* smart through a short probe window.
     *
     * Indexing happens in waves on cold machines, so a single `waitForSmartMode` can return inside
     * a momentary smart window right before the next wave starts. After each successful wait this
     * re-checks dumb-mode state following [SETTLE_PROBE_MS]; only a quiet probe counts as settled.
     * Bounded by [INDEXING_TIMEOUT_MS] so a stuck index fails reportably rather than hanging.
     */
    private fun waitUntilSmartAndSettled(dumbService: DumbService) {
        val deadline = System.currentTimeMillis() + INDEXING_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val remainingMs = deadline - System.currentTimeMillis()
            check(dumbService.waitForSmartMode(remainingMs)) {
                "project did not reach smart mode within ${INDEXING_TIMEOUT_MS}ms"
            }
            Thread.sleep(SETTLE_PROBE_MS)
            if (!dumbService.isDumb) {
                return
            }
        }
        error("smart mode did not stabilize within ${INDEXING_TIMEOUT_MS}ms")
    }

    /**
     * Re-settles smart mode for a retry, reporting (not throwing) when stabilization fails.
     */
    private fun settleSafely(
        dumbService: DumbService,
        benchmarkId: String,
    ): Boolean =
        try {
            waitUntilSmartAndSettled(dumbService)
            true
        } catch (exception: IllegalStateException) {
            logger.log(
                "conversion_smart_mode_not_recovered",
                mapOf("benchmark_id" to benchmarkId, "error" to exception.describe()),
            )
            false
        }

    /**
     * Retries conversion one file at a time so converter bugs become evaluation data.
     *
     * For smart-mode engines each file waits out any active indexing wave first, and a file that
     * fails on index readiness is retried once after re-settling, so one wave does not fail the
     * whole tail of the list.
     */
    private fun convertFilesIndividually(
        engine: J2kConversionEngine,
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
        relativePaths: Map<PsiJavaFile, Path>,
        config: BenchmarkConfig,
        batchFailure: Throwable,
    ): J2kRunResult {
        val convertedFiles = linkedMapOf<Path, String>()
        val errors = mutableListOf<String>()
        val sortedJavaFiles = javaFiles.sortedBy { relativePaths.getValue(it).toString() }

        sortedJavaFiles.forEach { javaFile ->
            val relativePath = relativePaths.getValue(javaFile)
            try {
                if (engine.requiresSmartMode && dumbService.isDumb) {
                    waitUntilSmartAndSettled(dumbService)
                }
                convertedFiles[relativePath] = convertSingleFile(engine, project, module, dumbService, javaFile)
            } catch (exception: Throwable) {
                exception.rethrowIfFatal()
                val recovered =
                    if (engine.requiresSmartMode && exception.isIndexNotReady()) {
                        retryFileAfterSettle(engine, project, module, dumbService, javaFile, relativePath, config)
                    } else {
                        null
                    }
                if (recovered != null) {
                    convertedFiles[relativePath] = recovered
                } else {
                    errors += "$relativePath: ${exception.describe()}"
                    logger.log(
                        "conversion_j2k_file_failed",
                        mapOf(
                            "benchmark_id" to config.id,
                            "relative_path" to relativePath.toString(),
                            "error" to exception.describe(),
                            "stack_trace" to exception.stackTraceToString(),
                        ),
                    )
                }
            }
        }

        return J2kRunResult(
            convertedFiles = convertedFiles,
            warnings = listOf("Batch J2K failed; retried conversion per file. Batch error: ${batchFailure.describe()}"),
            errors = errors,
        )
    }

    /**
     * Converts one file with a fresh converter invocation.
     */
    private fun convertSingleFile(
        engine: J2kConversionEngine,
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFile: PsiJavaFile,
    ): String {
        val results = engine.convert(project, module, dumbService, listOf(javaFile))
        require(results.size == 1) {
            "J2K per-file result count mismatch: expected 1, found ${results.size}"
        }
        return results.single()
    }

    /**
     * Retries one file after re-settling smart mode; returns null (to record the original error)
     * when stabilization or the retry itself fails.
     */
    private fun retryFileAfterSettle(
        engine: J2kConversionEngine,
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFile: PsiJavaFile,
        relativePath: Path,
        config: BenchmarkConfig,
    ): String? {
        logger.log(
            "conversion_smart_mode_lost",
            mapOf("benchmark_id" to config.id, "phase" to "file", "relative_path" to relativePath.toString()),
        )
        if (!settleSafely(dumbService, config.id)) {
            return null
        }
        return try {
            convertSingleFile(engine, project, module, dumbService, javaFile)
        } catch (retryException: Throwable) {
            retryException.rethrowIfFatal()
            null
        }
    }

    private companion object {
        /**
         * Upper bound for headless indexing. The disposable project is tiny, so real indexing takes
         * seconds; this ceiling only guards the never-completes case. Override via
         * `-Dj2k.indexing.timeoutMs=...`.
         */
        private val INDEXING_TIMEOUT_MS: Long = System.getProperty("j2k.indexing.timeoutMs")?.toLongOrNull() ?: 300_000L

        /** How long smart mode must stay quiet after a wait before it counts as settled. */
        private const val SETTLE_PROBE_MS: Long = 1_500L

        /** How many times the whole batch is retried after an indexing wave interrupts it. */
        private const val MAX_BATCH_RETRIES: Int = 1
    }
}

/**
 * Returns whether this failure (or any of its causes) is IntelliJ's index-not-ready signal.
 */
private fun Throwable.isIndexNotReady(): Boolean = generateSequence(this) { it.cause }.any { it is IndexNotReadyException }

/**
 * Raw J2K outputs and reportable converter failures for one benchmark.
 */
data class J2kRunResult(
    val convertedFiles: Map<Path, String>,
    val warnings: List<String>,
    val errors: List<String>,
)
