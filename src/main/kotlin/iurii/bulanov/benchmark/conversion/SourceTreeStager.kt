package iurii.bulanov.benchmark.conversion

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.streams.asSequence

/**
 * Copies benchmark checkout sources into a disposable staging tree for J2K.
 */
class SourceTreeStager(
    private val excludedDirectoryNames: Set<String> = setOf(".git", ".gradle", ".idea", "build", "out", "target"),
) {
    /**
     * Recreates [stagingDirectory] from [sourceDirectory], excluding generated/cache directories.
     */
    fun stage(
        sourceDirectory: Path,
        stagingDirectory: Path,
    ): SourceStagingResult {
        if (!Files.isDirectory(sourceDirectory)) {
            throw ConversionException("benchmark checkout directory does not exist: $sourceDirectory")
        }
        recreateDirectory(stagingDirectory)

        var copiedFileCount = 0
        Files.walk(sourceDirectory).use { stream ->
            stream
                .asSequence()
                .filter { it != sourceDirectory }
                .filterNot { sourceDirectory.relativize(it).hasExcludedSegment() }
                .forEach { source ->
                    val target = stagingDirectory.resolve(sourceDirectory.relativize(source)).normalize()
                    if (Files.isDirectory(source)) {
                        target.createDirectories()
                    } else if (Files.isRegularFile(source)) {
                        target.parent?.createDirectories()
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                        copiedFileCount += 1
                    }
                }
        }

        return SourceStagingResult(
            sourceDirectory = sourceDirectory,
            stagingDirectory = stagingDirectory,
            copiedFileCount = copiedFileCount,
        )
    }

    /**
     * Deletes and recreates an output directory.
     */
    private fun recreateDirectory(path: Path) {
        if (path.exists()) {
            path.toFile().deleteRecursively()
        }
        path.createDirectories()
    }

    /**
     * Returns true when any relative path segment is excluded from staging.
     */
    private fun Path.hasExcludedSegment(): Boolean = any { excludedDirectoryNames.contains(it.toString()) }
}
