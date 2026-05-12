package iurii.bulanov.benchmark.conversion

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

/**
 * Copies generated Kotlin files from staged source roots into the evaluator output tree.
 */
class GeneratedKotlinCollector {
    /**
     * Recreates [generatedDirectory] and copies `.kt` files from staged source roots.
     */
    fun collectFromStaging(
        stagingDirectory: Path,
        sourceRoots: List<String>,
        generatedDirectory: Path,
    ): GeneratedKotlinCollectionResult {
        recreateDirectory(generatedDirectory)
        val warnings = mutableListOf<String>()
        val generatedFiles = mutableListOf<Path>()

        sourceRoots.forEach { sourceRoot ->
            val stagedSourceRoot = stagingDirectory.resolve(sourceRoot).normalize()
            if (!Files.isDirectory(stagedSourceRoot)) {
                warnings += "staged source root missing while collecting Kotlin output: $sourceRoot"
                return@forEach
            }

            Files.walk(stagedSourceRoot).use { stream ->
                stream
                    .asSequence()
                    .filter { Files.isRegularFile(it) && it.extension == "kt" }
                    .forEach { source ->
                        val target = generatedDirectory.resolve(stagedSourceRoot.relativize(source)).normalize()
                        if (target.exists()) {
                            warnings += "generated Kotlin path collision: ${generatedDirectory.relativize(target)}"
                        }
                        target.parent?.createDirectories()
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                        generatedFiles.add(target)
                    }
            }
        }

        return GeneratedKotlinCollectionResult(
            generatedDirectory = generatedDirectory,
            generatedFiles = generatedFiles.sortedBy { generatedDirectory.relativize(it).toString() },
            warnings = warnings,
        )
    }

    /**
     * Writes converted Kotlin code returned directly from J2K into [generatedDirectory].
     */
    fun writeConvertedFiles(
        generatedDirectory: Path,
        convertedFiles: Map<Path, String>,
    ): GeneratedKotlinCollectionResult {
        recreateDirectory(generatedDirectory)
        val generatedFiles = mutableListOf<Path>()
        val warnings = mutableListOf<String>()

        convertedFiles.toSortedMap(compareBy { it.toString() }).forEach { (relativeJavaPath, kotlinCode) ->
            val relativeKotlinPath =
                relativeJavaPath.parent?.resolve("${relativeJavaPath.nameWithoutExtension}.kt")
                    ?: Path.of("${relativeJavaPath.nameWithoutExtension}.kt")
            val target = generatedDirectory.resolve(relativeKotlinPath).normalize()
            if (target.exists()) {
                warnings += "generated Kotlin path collision: $relativeKotlinPath"
            }
            target.parent?.createDirectories()
            Files.writeString(target, kotlinCode)
            generatedFiles.add(target)
        }

        return GeneratedKotlinCollectionResult(
            generatedDirectory = generatedDirectory,
            generatedFiles = generatedFiles.sortedBy { generatedDirectory.relativize(it).toString() },
            warnings = warnings,
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
}
