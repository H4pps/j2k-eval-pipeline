@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.engine

import com.intellij.codeInsight.DefaultInferredAnnotationProvider
import com.intellij.codeInsight.InferredAnnotationProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.OldJ2kConverterExtension

/**
 * Static J2K conversion backed by the old (K1) converter.
 *
 * Encapsulates the K1-specific behavior: the disposable-project annotation workaround, the raw
 * [NoOpPostProcessor], and the basic-mode [ConverterSettings].
 */
class OldJ2kConversionEngine : J2kConversionEngine {
    override val id: String = "k1-old"

    /**
     * Removes index-backed inferred annotations from the disposable runner project.
     *
     * The old J2K converter asks Java PSI for inferred nullability and contracts. IntelliJ's
     * default provider may call bytecode-analysis indexes before the headless project becomes
     * smart, so the engine disables that provider and keeps explicit source annotations only.
     */
    @Suppress("DEPRECATION")
    override fun configureProject(project: Project): Map<String, Any?> {
        val extensionPoint = InferredAnnotationProvider.EP_NAME.getPoint(project)
        val providersToRemove = extensionPoint.extensionList.filterIsInstance<DefaultInferredAnnotationProvider>()
        providersToRemove.forEach(extensionPoint::unregisterExtension)
        return mapOf("disabled_provider_count" to providersToRemove.size)
    }

    /**
     * Converts a group of Java PSI files with a fresh old-J2K converter instance.
     */
    override fun convert(
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
