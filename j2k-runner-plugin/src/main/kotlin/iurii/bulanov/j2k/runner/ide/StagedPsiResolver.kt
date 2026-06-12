package iurii.bulanov.j2k.runner.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import iurii.bulanov.j2k.runner.normalizeLineEndings
import iurii.bulanov.source.SourceFileDiscovery
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves staged Java files to IntelliJ PSI and keeps the IDE VFS in sync with the staged tree.
 */
class StagedPsiResolver(
    private val sourceFileDiscovery: SourceFileDiscovery = SourceFileDiscovery(),
) {
    /**
     * Resolves staged Java files to IntelliJ PSI files.
     */
    fun findPsiJavaFiles(
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
     * Prevents stale IDE VFS/PSI content from being reported as a successful conversion.
     */
    fun verifyPsiMatchesDisk(javaFiles: List<PsiJavaFile>) {
        javaFiles.forEach { javaFile ->
            val filePath = Path.of(javaFile.virtualFile.path)
            val diskText = Files.readString(filePath).normalizeLineEndings()
            require(javaFile.text.normalizeLineEndings() == diskText) {
                "stale PSI content for staged Java file: $filePath"
            }
        }
    }

    /**
     * Forces IntelliJ's virtual file system to observe the freshly recreated staging tree.
     */
    fun refreshStagedSourceTree(stagingDirectory: Path) {
        val virtualRoot =
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(stagingDirectory)
                ?: error("failed to refresh staged source tree: $stagingDirectory")
        VfsUtil.markDirtyAndRefresh(false, true, true, virtualRoot)
    }

    /**
     * Converts an absolute Java file path into a source-root-relative path.
     */
    fun relativeSourcePath(
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
}
