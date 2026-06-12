package iurii.bulanov.benchmark.evaluation.scanning

import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Parses Kotlin source into PSI without semantic analysis.
 */
internal class KotlinPsiParser {
    private val kotlinDisposable = Disposer.newDisposable()
    private val kotlinPsiFactory: KtPsiFactory by lazy { KtPsiFactory(kotlinEnvironment().project, markGenerated = false) }

    /**
     * Parses [source] into a synthetic Kotlin file.
     */
    fun parse(source: String): KtFile = kotlinPsiFactory.createFile(KOTLIN_SYNTHETIC_FILE_NAME, source)

    @OptIn(K1Deprecation::class)
    private fun kotlinEnvironment(): KotlinCoreEnvironment {
        val configuration = CompilerConfiguration()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, KOTLIN_SYNTHETIC_MODULE_NAME)
        return KotlinCoreEnvironment.createForProduction(kotlinDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }

    private companion object {
        private const val KOTLIN_SYNTHETIC_FILE_NAME = "Source.kt"
        private const val KOTLIN_SYNTHETIC_MODULE_NAME = "j2k-eval"
    }
}
