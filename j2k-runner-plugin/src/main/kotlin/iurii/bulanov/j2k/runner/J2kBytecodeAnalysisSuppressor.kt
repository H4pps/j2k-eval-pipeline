package iurii.bulanov.j2k.runner

import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisSuppressor
import com.intellij.openapi.vfs.VirtualFile

/**
 * Disables bytecode-derived Java inference for the headless conversion project.
 *
 * The J2K runner converts source files in a short-lived IntelliJ project where bytecode indexes may
 * still be unavailable. Suppressing bytecode analysis keeps explicit source annotations available
 * while avoiding index-dependent inferred annotations and contracts.
 */
class J2kBytecodeAnalysisSuppressor : BytecodeAnalysisSuppressor {
    /**
     * Suppresses bytecode analysis for every class file queried during conversion.
     */
    override fun shouldSuppress(file: VirtualFile): Boolean = true

    /**
     * Separates the runner's bytecode-analysis cache shape from the platform default.
     */
    override fun getVersion(): Int = 2
}
