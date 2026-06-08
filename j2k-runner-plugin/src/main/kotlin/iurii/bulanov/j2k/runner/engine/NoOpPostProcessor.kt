package iurii.bulanov.j2k.runner.engine

import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.j2k.PostProcessingTarget
import org.jetbrains.kotlin.j2k.PostProcessor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

/**
 * Minimal post-processor that keeps conversion focused on raw static J2K output.
 */
internal object NoOpPostProcessor : PostProcessor {
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
