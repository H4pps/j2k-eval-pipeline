@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.engine

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import iurii.bulanov.benchmark.conversion.ConverterKind
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension

/**
 * Shared base for engines that drive an IntelliJ J2K converter selected by [ConverterKind].
 *
 * Holds the common conversion mechanics — extension lookup, full settings, and the
 * `filesToKotlin` call with the kind's real post-processor — so concrete per-version engines
 * only express what differs (e.g. [configureProject]). Each kind lives in its own subpackage.
 */
abstract class AbstractExtensionConversionEngine(
    kind: ConverterKind,
) : J2kConversionEngine {
    override val id: String = kind.id

    /** New IR-pipeline converters resolve via the Analysis API, so they need indexes. */
    override val requiresSmartMode: Boolean = true

    /** The IntelliJ converter selected for this engine. */
    protected val platformKind: J2kConverterExtension.Kind = J2kConverterExtension.Kind.valueOf(kind.platformKind)

    /**
     * Conversion settings used for all kinds. `basicMode = false` so the full conversion runs;
     * the same settings across kinds keep the comparison fair.
     */
    protected open val settings: ConverterSettings =
        ConverterSettings(
            forceNotNullTypes = true,
            specifyLocalVariableTypeByDefault = false,
            specifyFieldTypeByDefault = false,
            openByDefault = false,
            publicByDefault = false,
            basicMode = false,
        )

    /**
     * Engine-specific disposable-project setup. Default is a no-op; kinds override as needed.
     */
    override fun configureProject(project: Project): Map<String, Any?> = emptyMap()

    /**
     * Converts [javaFiles] with the selected converter and its real post-processor.
     */
    override fun convert(
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
    ): List<String> {
        val extension = J2kConverterExtension.extension(platformKind)
        val converter = extension.createJavaToKotlinConverter(project, module, settings, null)
        val postProcessor = extension.createPostProcessor(formatCode = true)
        return runConversion(dumbService) {
            runBlocking {
                val result = converter.filesToKotlin(
                    javaFiles,
                    postProcessor,
                )
                javaFiles.map { result.kotlinCodeByJavaFile.getValue(it) }
            }
        }
    }

    /**
     * Runs [block] for one converter invocation. Indexing/smart-mode is handled once by
     * `J2kConversionRunner` before conversion (see [requiresSmartMode]); the default just runs the
     * converter. The legacy converter overrides this to run in dumb mode (see `K1OldConversionEngine`).
     */
    protected open fun runConversion(
        dumbService: DumbService,
        block: () -> List<String>,
    ): List<String> = block()
}
