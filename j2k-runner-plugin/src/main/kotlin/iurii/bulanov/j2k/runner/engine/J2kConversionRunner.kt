@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.engine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
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
            val batchResult =
                try {
                    val results = engine.convert(project, module, dumbService, javaFiles)
                    require(results.size == javaFiles.size) {
                        "J2K result count mismatch: expected ${javaFiles.size}, found ${results.size}"
                    }
                    J2kRunResult(
                        convertedFiles =
                            javaFiles
                                .zip(results)
                                .associate { (javaFile, kotlinCode) -> relativePaths.getValue(javaFile) to kotlinCode },
                        warnings = emptyList(),
                        errors = emptyList(),
                    )
                } catch (exception: Throwable) {
                    exception.rethrowIfFatal()
                    logger.log(
                        "conversion_j2k_batch_failed",
                        mapOf(
                            "benchmark_id" to config.id,
                            "error" to exception.describe(),
                            "stack_trace" to exception.stackTraceToString(),
                        ),
                    )
                    convertFilesIndividually(
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
            ApplicationManager.getApplication().invokeAndWait {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
            batchResult
        } finally {
            projectFactory.closeProject(project)
        }
    }

    /**
     * Triggers an unindexed-files scan and waits, with a hard time bound, for the project to reach
     * smart mode — required before the new (K1_NEW/K2) converters resolve through the Analysis API.
     *
     * Done once per run (not per converter call) so the per-file fallback never re-indexes. The
     * project is created via the IDE open path, which schedules indexing; queuing a scanner is a
     * backstop, and `waitForSmartMode` is bounded so a stuck index becomes a reportable failure
     * rather than a hang. Safe to block here because the runner executes off the EDT.
     */
    private fun indexProject(
        project: Project,
        dumbService: DumbService,
        benchmarkId: String,
    ) {
        UnindexedFilesScanner(project, "j2k-runner conversion").queue()
        val startMs = System.currentTimeMillis()
        val reachedSmartMode = dumbService.waitForSmartMode(INDEXING_TIMEOUT_MS)
        logger.log(
            "conversion_indexing_completed",
            mapOf(
                "benchmark_id" to benchmarkId,
                "reached_smart_mode" to reachedSmartMode,
                "elapsed_ms" to (System.currentTimeMillis() - startMs),
            ),
        )
        check(reachedSmartMode) { "project did not reach smart mode within ${INDEXING_TIMEOUT_MS}ms" }
    }

    /**
     * Retries conversion one file at a time so converter bugs become evaluation data.
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
                val results = engine.convert(project, module, dumbService, listOf(javaFile))
                require(results.size == 1) {
                    "J2K per-file result count mismatch: expected 1, found ${results.size}"
                }
                convertedFiles[relativePath] = results.single()
            } catch (exception: Throwable) {
                exception.rethrowIfFatal()
                val error = "$relativePath: ${exception.describe()}"
                errors += error
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

        return J2kRunResult(
            convertedFiles = convertedFiles,
            warnings = listOf("Batch J2K failed; retried conversion per file. Batch error: ${batchFailure.describe()}"),
            errors = errors,
        )
    }

    private companion object {
        /**
         * Upper bound for headless indexing. The disposable project is tiny, so real indexing takes
         * seconds; this ceiling only guards the never-completes case. Override via
         * `-Dj2k.indexing.timeoutMs=...`.
         */
        private val INDEXING_TIMEOUT_MS: Long = System.getProperty("j2k.indexing.timeoutMs")?.toLongOrNull() ?: 300_000L
    }
}

/**
 * Raw J2K outputs and reportable converter failures for one benchmark.
 */
data class J2kRunResult(
    val convertedFiles: Map<Path, String>,
    val warnings: List<String>,
    val errors: List<String>,
)
