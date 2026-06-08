package iurii.bulanov.j2k.runner.engine

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile

/**
 * Strategy for one static J2K converter variant.
 *
 * The old (K1) converter is the only implementation today ([OldJ2kConversionEngine]). A future
 * new (K2) converter implements this same interface so the rest of the runner — project setup,
 * PSI resolution, batch/per-file orchestration, reporting — stays unchanged. See
 * [J2kConversionEngineFactory] for the single place that selects the engine.
 */
interface J2kConversionEngine {
    /**
     * Stable identifier used in structured logs (and, later, conversion reports), e.g. `k1-old`.
     */
    val id: String

    /**
     * Applies engine-specific setup to the disposable conversion [project].
     *
     * Returns a small map of fields describing what was configured, for structured logging.
     */
    fun configureProject(project: Project): Map<String, Any?>

    /**
     * Converts [javaFiles] to Kotlin source text, returning one result per input in the same order.
     */
    fun convert(
        project: Project,
        module: Module,
        dumbService: DumbService,
        javaFiles: List<PsiJavaFile>,
    ): List<String>
}
