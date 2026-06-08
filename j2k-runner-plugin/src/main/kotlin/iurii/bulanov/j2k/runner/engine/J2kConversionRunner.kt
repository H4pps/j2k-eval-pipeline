package iurii.bulanov.j2k.runner.engine

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiJavaFile
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
        val project = projectFactory.createProject("j2k-${config.id}", paths.stagingDirectory)
        return try {
            logger.log("conversion_project_created", mapOf("benchmark_id" to config.id, "project_name" to project.name))
            logger.log(
                "conversion_inferred_annotations_configured",
                mapOf("benchmark_id" to config.id) + engine.configureProject(project),
            )
            val module = projectFactory.createModule(project, paths.stagingDirectory, config.java.sourceRoots)
            logger.log("conversion_module_created", mapOf("benchmark_id" to config.id, "module_name" to module.name))
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
            val dumbService = DumbService.getInstance(project)
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
}

/**
 * Raw J2K outputs and reportable converter failures for one benchmark.
 */
data class J2kRunResult(
    val convertedFiles: Map<Path, String>,
    val warnings: List<String>,
    val errors: List<String>,
)
