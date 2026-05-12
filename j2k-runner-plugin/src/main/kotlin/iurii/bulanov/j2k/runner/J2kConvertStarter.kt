@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner

import com.intellij.codeInsight.DefaultInferredAnnotationProvider
import com.intellij.codeInsight.InferredAnnotationProvider
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import iurii.bulanov.benchmark.config.BenchmarkConfig
import iurii.bulanov.benchmark.config.BenchmarkConfigParser
import iurii.bulanov.benchmark.conversion.ConversionPathOverrides
import iurii.bulanov.benchmark.conversion.ConversionPathResolver
import iurii.bulanov.benchmark.conversion.ConversionPaths
import iurii.bulanov.benchmark.conversion.ConversionReport
import iurii.bulanov.benchmark.conversion.ConversionReportWriter
import iurii.bulanov.benchmark.conversion.ConversionStatus
import iurii.bulanov.benchmark.conversion.GeneratedKotlinCollector
import iurii.bulanov.benchmark.conversion.SourceTreeStager
import iurii.bulanov.logging.JsonEncoder
import iurii.bulanov.source.SourceFileDiscovery
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.OldJ2kConverterExtension
import org.jetbrains.kotlin.j2k.PostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Headless IntelliJ application starter that runs static J2K for a benchmark.
 */
class J2kConvertStarter : ApplicationStarter {
    private val configParser = BenchmarkConfigParser()
    private val pathResolver = ConversionPathResolver()
    private val sourceFileDiscovery = SourceFileDiscovery()
    private val sourceTreeStager = SourceTreeStager()
    private val generatedKotlinCollector = GeneratedKotlinCollector()
    private val reportWriter = ConversionReportWriter()

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

            log(
                paths,
                "conversion_started",
                mapOf(
                    "benchmark_id" to config.id,
                    "config_path" to options.configPath.toString(),
                    "idea_kotlin_plugin_use_k2" to System.getProperty("idea.kotlin.plugin.use.k2"),
                ),
            )
            val checkoutDirectory = harnessRoot.resolve(config.checkout.directory).normalize()
            val javaFiles = sourceFileDiscovery.discoverJavaFiles(checkoutDirectory, config.java.sourceRoots).files
            log(
                paths,
                "conversion_sources_discovered",
                mapOf(
                    "benchmark_id" to config.id,
                    "checkout_directory" to checkoutDirectory.toString(),
                    "source_java_file_count" to javaFiles.size,
                ),
            )

            val stagingResult = sourceTreeStager.stage(checkoutDirectory, paths.stagingDirectory)
            log(
                paths,
                "conversion_sources_staged",
                mapOf(
                    "benchmark_id" to config.id,
                    "staging_directory" to paths.stagingDirectory.toString(),
                    "copied_file_count" to stagingResult.copiedFileCount,
                ),
            )
            val j2kResult = runJ2k(config, paths, javaFiles.size)
            warnings += j2kResult.warnings
            errors += j2kResult.errors
            log(
                paths,
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
            log(
                paths,
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
            log(
                paths,
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
                log(
                    safePaths,
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
     * Runs J2K against staged Java files and returns source-root-relative Kotlin code.
     */
    private fun runJ2k(
        config: BenchmarkConfig,
        paths: ConversionPaths,
        sourceJavaFileCount: Int,
    ): J2kRunResult {
        val project = createProject(config, paths.stagingDirectory)
        return try {
            log(paths, "conversion_project_created", mapOf("benchmark_id" to config.id, "project_name" to project.name))
            val disabledProviderCount = disableIndexBackedInferredAnnotations(project)
            log(
                paths,
                "conversion_inferred_annotations_configured",
                mapOf(
                    "benchmark_id" to config.id,
                    "disabled_provider_count" to disabledProviderCount,
                ),
            )
            val module = createModule(project, paths.stagingDirectory, config.java.sourceRoots)
            log(paths, "conversion_module_created", mapOf("benchmark_id" to config.id, "module_name" to module.name))
            val javaFiles =
                ApplicationManager.getApplication().runReadAction(
                    Computable {
                        findPsiJavaFiles(project, paths.stagingDirectory, config.java.sourceRoots)
                    },
                )
            require(javaFiles.size == sourceJavaFileCount) {
                "staged Java file count mismatch: expected $sourceJavaFileCount, found ${javaFiles.size}"
            }
            log(
                paths,
                "conversion_psi_files_resolved",
                mapOf("benchmark_id" to config.id, "psi_java_file_count" to javaFiles.size),
            )

            val relativePaths =
                javaFiles.associateWith { javaFile ->
                    relativeSourcePath(paths.stagingDirectory, config.java.sourceRoots, Path.of(javaFile.virtualFile.path))
                }
            val dumbService = DumbService.getInstance(project)
            val batchResult =
                try {
                    val results = convertJavaFiles(project, module, dumbService, javaFiles)
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
                    log(
                        paths,
                        "conversion_j2k_batch_failed",
                        mapOf(
                            "benchmark_id" to config.id,
                            "error" to exception.describe(),
                            "stack_trace" to exception.stackTraceToString(),
                        ),
                    )
                    convertFilesIndividually(
                        project = project,
                        module = module,
                        dumbService = dumbService,
                        javaFiles = javaFiles,
                        relativePaths = relativePaths,
                        paths = paths,
                        config = config,
                        batchFailure = exception,
                    )
                }
            ApplicationManager.getApplication().invokeAndWait {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
            batchResult
        } finally {
            closeProject(project)
        }
    }

    /**
     * Converts a group of Java PSI files with a fresh old-J2K converter instance.
     */
    private fun convertJavaFiles(
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
    ): List<String> {
        val converter = OldJ2kConverterExtension().createJavaToKotlinConverter(project, module, BASIC_CONVERTER_SETTINGS, null)
        val accessToken = dumbService.runWithWaitForSmartModeDisabled()
        return try {
            dumbService.computeWithAlternativeResolveEnabled(
                ThrowableComputable {
                    converter
                        .filesToKotlin(
                            javaFiles,
                            NoOpPostProcessor,
                            EmptyProgressIndicator(),
                            emptyList(),
                            emptyList(),
                        ).results
                },
            )
        } finally {
            accessToken.finish()
        }
    }

    /**
     * Retries conversion one file at a time so converter bugs become evaluation data.
     */
    private fun convertFilesIndividually(
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
        relativePaths: Map<PsiJavaFile, Path>,
        paths: ConversionPaths,
        config: BenchmarkConfig,
        batchFailure: Throwable,
    ): J2kRunResult {
        val convertedFiles = linkedMapOf<Path, String>()
        val errors = mutableListOf<String>()
        val sortedJavaFiles = javaFiles.sortedBy { relativePaths.getValue(it).toString() }

        sortedJavaFiles.forEach { javaFile ->
            val relativePath = relativePaths.getValue(javaFile)
            try {
                val results = convertJavaFiles(project, module, dumbService, listOf(javaFile))
                require(results.size == 1) {
                    "J2K per-file result count mismatch: expected 1, found ${results.size}"
                }
                convertedFiles[relativePath] = results.single()
            } catch (exception: Throwable) {
                exception.rethrowIfFatal()
                val error = "$relativePath: ${exception.describe()}"
                errors += error
                log(
                    paths,
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

    /**
     * Creates a disposable project rooted at the staged benchmark source tree.
     */
    private fun createProject(
        config: BenchmarkConfig,
        stagingDirectory: Path,
    ): Project {
        val projectName = "j2k-${config.id}"
        val openProjectTask =
            OpenProjectTask
                .build()
                .asNewProject()
                .withProjectName(projectName)
        return ProjectManagerEx.getInstanceEx().newProject(stagingDirectory, openProjectTask)
            ?: error("failed to create IntelliJ project for $stagingDirectory")
    }

    /**
     * Removes index-backed inferred annotations from the disposable runner project.
     *
     * The old J2K converter asks Java PSI for inferred nullability and contracts. IntelliJ's
     * default provider may call bytecode-analysis indexes before the headless project becomes
     * smart, so the runner disables that provider and keeps explicit source annotations only.
     */
    @Suppress("DEPRECATION")
    private fun disableIndexBackedInferredAnnotations(project: Project): Int {
        val extensionPoint = InferredAnnotationProvider.EP_NAME.getPoint(project)
        val providersToRemove = extensionPoint.extensionList.filterIsInstance<DefaultInferredAnnotationProvider>()
        providersToRemove.forEach(extensionPoint::unregisterExtension)
        return providersToRemove.size
    }

    /**
     * Creates a disposable Java module with configured source roots.
     */
    private fun createModule(
        project: Project,
        stagingDirectory: Path,
        sourceRoots: List<String>,
    ): Module =
        WriteAction.computeAndWait<Module, RuntimeException> {
            val sdk = createRunnerJdk()
            val modulePath = stagingDirectory.resolve("${project.name}.iml")
            val module =
                ModuleManager.getInstance(project).newModule(
                    modulePath.toString(),
                    StdModuleTypes.JAVA.id,
                )
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.sdk = sdk
                attachKotlinStdlib(model)
                val contentEntry = model.addContentEntry(VfsUtil.pathToUrl(stagingDirectory.toString()))
                sourceRoots.forEach { sourceRoot ->
                    contentEntry.addSourceFolder(VfsUtil.pathToUrl(stagingDirectory.resolve(sourceRoot).toString()), false)
                }
            }
            module
        }

    /**
     * Adds the IDE-bundled Kotlin standard library to the temporary module.
     */
    private fun attachKotlinStdlib(model: ModifiableRootModel) {
        val stdlibJars = kotlinStdlibJars()
        require(stdlibJars.isNotEmpty()) {
            "failed to locate bundled Kotlin stdlib jars under ${PathManager.getHomePath()}"
        }
        val library = model.moduleLibraryTable.createLibrary("kotlin-stdlib")
        val libraryModel = library.modifiableModel
        stdlibJars.forEach { jar ->
            libraryModel.addRoot(VfsUtil.getUrlForLibraryRoot(jar), OrderRootType.CLASSES)
        }
        libraryModel.commit()
    }

    /**
     * Resolves Kotlin stdlib jars from the bundled Kotlin plugin.
     */
    private fun kotlinStdlibJars(): List<Path> {
        val kotlinLibDirectory = Path.of(PathManager.getHomePath(), "plugins", "Kotlin", "kotlinc", "lib")
        return listOf(
            "kotlin-stdlib.jar",
            "kotlin-stdlib-jdk7.jar",
            "kotlin-stdlib-jdk8.jar",
        ).map { kotlinLibDirectory.resolve(it) }.filter(Files::exists)
    }

    /**
     * Creates or reuses a JDK SDK backed by the IDE runner JVM.
     */
    private fun createRunnerJdk(): Sdk {
        val jdkName = "j2k-runner-jdk"
        val projectJdkTable = ProjectJdkTable.getInstance()
        return projectJdkTable.findJdk(jdkName)
            ?: JavaSdk.getInstance().createJdk(jdkName, System.getProperty("java.home"), false).also { sdk ->
                projectJdkTable.addJdk(sdk)
            }
    }

    /**
     * Closes the temporary IDE project with the write-intent context required by the platform.
     */
    private fun closeProject(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            ProjectManagerEx.getInstanceEx().forceCloseProject(project, true)
        } else {
            application.invokeAndWait {
                ProjectManagerEx.getInstanceEx().forceCloseProject(project, true)
            }
        }
    }

    /**
     * Resolves staged Java files to IntelliJ PSI files.
     */
    private fun findPsiJavaFiles(
        project: Project,
        stagingDirectory: Path,
        sourceRoots: List<String>,
    ): List<PsiJavaFile> =
        sourceFileDiscovery
            .discoverJavaFiles(stagingDirectory, sourceRoots)
            .files
            .map { discovered ->
                val virtualFile =
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(discovered.absolutePath)
                        ?: error("failed to resolve staged Java file: ${discovered.absolutePath}")
                PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile
                    ?: error("staged file is not a Java PSI file: ${discovered.absolutePath}")
            }

    /**
     * Converts an absolute Java file path into a source-root-relative path.
     */
    private fun relativeSourcePath(
        stagingDirectory: Path,
        sourceRoots: List<String>,
        file: Path,
    ): Path {
        val normalizedFile = file.normalize()
        sourceRoots.forEach { sourceRoot ->
            val sourceRootPath = stagingDirectory.resolve(sourceRoot).normalize()
            if (normalizedFile.startsWith(sourceRootPath)) {
                return sourceRootPath.relativize(normalizedFile)
            }
        }
        return stagingDirectory.relativize(normalizedFile)
    }

    /**
     * Emits a structured log line to stdout and the runner log file.
     */
    private fun log(
        paths: ConversionPaths,
        event: String,
        fields: Map<String, Any?>,
    ) {
        val payload = JsonEncoder.encode(linkedMapOf("level" to "INFO", "event" to event) + fields)
        println(payload)
        val logPath = paths.logsDirectory.resolve("j2k-runner.log")
        if (!Files.exists(logPath)) {
            logPath.parent.createDirectories()
            logPath.writeText("")
        }
        Files.writeString(logPath, payload + "\n", java.nio.file.StandardOpenOption.APPEND)
    }

    /**
     * Produces a compact error string with the root cause when available.
     */
    private fun Throwable.describe(): String {
        val rootCause = generateSequence(this) { it.cause }.last()
        val rootMessage = rootCause.message ?: rootCause::class.java.name
        return if (rootCause === this) {
            rootMessage
        } else {
            "${this::class.java.name}: $rootMessage"
        }
    }

    /**
     * Rethrows JVM-level fatal failures while keeping converter assertions reportable.
     */
    private fun Throwable.rethrowIfFatal() {
        if (this is ThreadDeath || this is VirtualMachineError || this is InterruptedException) {
            throw this
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

/**
 * Raw J2K outputs and reportable converter failures for one benchmark.
 */
private data class J2kRunResult(
    val convertedFiles: Map<Path, String>,
    val warnings: List<String>,
    val errors: List<String>,
)

/**
 * Parsed command-line options for the headless J2K runner.
 */
private data class J2kRunnerOptions(
    val configPath: Path,
    val stagingDirectory: Path?,
    val generatedKotlinDirectory: Path?,
    val conversionReport: Path?,
    val logsDirectory: Path?,
) {
    /**
     * Converts parsed options into conversion path overrides.
     */
    fun toPathOverrides(): ConversionPathOverrides =
        ConversionPathOverrides(
            stagingDirectory = stagingDirectory,
            generatedKotlinDirectory = generatedKotlinDirectory,
            conversionReport = conversionReport,
            logsDirectory = logsDirectory,
        )

    /**
     * Resolves the harness repository root from the benchmark config location.
     */
    fun harnessRoot(): Path {
        val absoluteConfigPath = configPath.toAbsolutePath().normalize()
        val configDirectory = absoluteConfigPath.parent ?: Path.of("").toAbsolutePath().normalize()
        return if (configDirectory.fileName?.toString() == "benchmarks") {
            configDirectory.parent ?: configDirectory
        } else {
            configDirectory
        }
    }

    companion object {
        /**
         * Parses runner arguments after the `j2k-convert` command token.
         */
        fun parse(args: List<String>): J2kRunnerOptions {
            val values = mutableMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val option = args[index]
                if (!option.startsWith("--")) {
                    throw IllegalArgumentException("unexpected argument: $option")
                }
                val value = args.getOrNull(index + 1) ?: throw IllegalArgumentException("missing value for $option")
                values[option] = value
                index += 2
            }

            val config = values.remove("--config") ?: throw IllegalArgumentException("missing required --config")
            val stagingDirectory = values.remove("--staging-dir")?.let { Path.of(it) }
            val generatedKotlinDirectory = values.remove("--generated-kotlin-dir")?.let { Path.of(it) }
            val conversionReport = values.remove("--conversion-report")?.let { Path.of(it) }
            val logsDirectory = values.remove("--logs-dir")?.let { Path.of(it) }
            if (values.isNotEmpty()) {
                throw IllegalArgumentException("unknown options: ${values.keys.sorted().joinToString(", ")}")
            }

            return J2kRunnerOptions(
                configPath = Path.of(config),
                stagingDirectory = stagingDirectory,
                generatedKotlinDirectory = generatedKotlinDirectory,
                conversionReport = conversionReport,
                logsDirectory = logsDirectory,
            )
        }
    }
}

/**
 * Minimal postprocessor used to keep Phase 3 focused on raw static J2K output.
 */
private object NoOpPostProcessor : PostProcessor {
    override val phasesCount: Int = 0

    override fun insertImport(
        file: KtFile,
        fqName: FqName,
    ) = Unit

    override fun doAdditionalProcessing(
        target: PostProcessingTarget,
        converterContext: ConverterContext?,
        onPhaseChanged: ((Int, String) -> Unit)?,
    ) = Unit
}

private val BASIC_CONVERTER_SETTINGS =
    ConverterSettings(
        forceNotNullTypes = true,
        specifyLocalVariableTypeByDefault = false,
        specifyFieldTypeByDefault = false,
        openByDefault = false,
        publicByDefault = false,
        basicMode = true,
    )
