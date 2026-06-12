@file:Suppress("UnstableApiUsage")

package iurii.bulanov.j2k.runner.engine.k1old

import com.intellij.codeInsight.DefaultInferredAnnotationProvider
import com.intellij.codeInsight.InferredAnnotationProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.AbstractExtensionConversionEngine

/**
 * Legacy (`K1_OLD`) converter in dumb mode — the isolated baseline, no indexing.
 *
 * Keeps the K1 isolation workaround: the old converter asks Java PSI for inferred nullability and
 * contracts, which can hit bytecode-analysis indexes before the project is smart. Removing the
 * default inferred-annotation provider keeps explicit source annotations only and avoids
 * `IndexNotReadyException`. Because it does not require smart mode, the runner creates the project
 * without indexing (`newProject`), so there is no background indexing to race.
 */
class K1OldDumbConversionEngine : AbstractExtensionConversionEngine(ConverterKind.K1_OLD_DUMB) {
    /** The dumb-mode converter must not trigger or wait on indexing. */
    override val requiresSmartMode: Boolean = false

    @Suppress("DEPRECATION")
    override fun configureProject(project: Project): Map<String, Any?> {
        val extensionPoint = InferredAnnotationProvider.EP_NAME.getPoint(project)
        val providersToRemove = extensionPoint.extensionList.filterIsInstance<DefaultInferredAnnotationProvider>()
        providersToRemove.forEach(extensionPoint::unregisterExtension)
        return mapOf("disabled_provider_count" to providersToRemove.size)
    }

    /**
     * Runs in dumb mode with alternative resolution enabled, avoiding the index-readiness work the
     * indexed kinds require.
     */
    override fun runConversion(
        dumbService: DumbService,
        block: () -> List<String>,
    ): List<String> {
        val accessToken = dumbService.runWithWaitForSmartModeDisabled()
        return try {
            dumbService.computeWithAlternativeResolveEnabled(ThrowableComputable { block() })
        } finally {
            accessToken.finish()
        }
    }
}
