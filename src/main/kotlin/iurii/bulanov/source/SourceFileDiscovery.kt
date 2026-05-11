package iurii.bulanov.source

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Discovers source files used by benchmark checkout and evaluation steps.
 */
class SourceFileDiscovery {
    /**
     * Resolves configured Java source roots and discovers `.java` files recursively.
     */
    fun discoverJavaFiles(
        checkoutDirectory: Path,
        sourceRoots: List<String>,
    ): SourceDiscoveryResult {
        val normalizedCheckout = checkoutDirectory.normalize()
        val missingRoots = mutableListOf<String>()
        val files = mutableListOf<DiscoveredSourceFile>()

        sourceRoots.forEach { sourceRoot ->
            val rootPath = resolveRelativeDirectory(normalizedCheckout, sourceRoot, "java.sourceRoots")
            if (!rootPath.isDirectory()) {
                missingRoots += sourceRoot
            } else {
                files += discoverFiles(rootPath, normalizedCheckout, ".java")
            }
        }

        if (missingRoots.isNotEmpty()) {
            throw SourceDiscoveryException("missing Java source roots: ${missingRoots.joinToString(", ")}")
        }
        if (files.isEmpty()) {
            throw SourceDiscoveryException("no Java files found under configured source roots")
        }

        return SourceDiscoveryResult(rootDirectory = normalizedCheckout, directoryExists = true, files = files.sortedByPath())
    }

    /**
     * Discovers `.kt` files recursively under the generated Kotlin output directory.
     */
    fun discoverKotlinFiles(generatedKotlinDirectory: Path): SourceDiscoveryResult {
        val normalizedDirectory = generatedKotlinDirectory.normalize()
        if (!normalizedDirectory.isDirectory()) {
            return SourceDiscoveryResult(rootDirectory = normalizedDirectory, directoryExists = false, files = emptyList())
        }
        return SourceDiscoveryResult(
            rootDirectory = normalizedDirectory,
            directoryExists = true,
            files = discoverFiles(normalizedDirectory, normalizedDirectory, ".kt").sortedByPath(),
        )
    }

    /**
     * Counts Java files while preserving the Phase 1 checkout validator contract.
     */
    fun countJavaFiles(
        checkoutDirectory: Path,
        sourceRoots: List<String>,
    ): Int = discoverJavaFiles(checkoutDirectory, sourceRoots).files.size

    /**
     * Finds files with [extension] under [sourceRootPath] and stores paths relative to [relativeRoot].
     */
    private fun discoverFiles(
        sourceRootPath: Path,
        relativeRoot: Path,
        extension: String,
    ): List<DiscoveredSourceFile> =
        Files.walk(sourceRootPath).use { stream ->
            stream
                .asSequence()
                .filter { Files.isRegularFile(it) && it.name.endsWith(extension) }
                .map { file ->
                    val normalizedFile = file.normalize()
                    DiscoveredSourceFile(
                        absolutePath = normalizedFile,
                        relativePath = relativeRoot.relativize(normalizedFile),
                    )
                }.toList()
        }

    /**
     * Resolves and validates a relative source root under [checkoutDirectory].
     */
    private fun resolveRelativeDirectory(
        checkoutDirectory: Path,
        relativePath: String,
        fieldName: String,
    ): Path {
        val path = Paths.get(relativePath)
        if (path.isAbsolute) {
            throw SourceDiscoveryException("$fieldName entries must be relative paths: $relativePath")
        }
        val resolved = checkoutDirectory.resolve(path).normalize()
        if (!resolved.startsWith(checkoutDirectory)) {
            throw SourceDiscoveryException("$fieldName entry escapes checkout directory: $relativePath")
        }
        return resolved
    }

    /**
     * Sorts discovered files by relative path for deterministic reports and tests.
     */
    private fun List<DiscoveredSourceFile>.sortedByPath(): List<DiscoveredSourceFile> = sortedBy { it.relativePath.toString() }
}

/**
 * A source file discovered under a benchmark source or generated output root.
 */
data class DiscoveredSourceFile(
    val absolutePath: Path,
    val relativePath: Path,
)

/**
 * File discovery result for one logical source root.
 */
data class SourceDiscoveryResult(
    val rootDirectory: Path,
    val directoryExists: Boolean,
    val files: List<DiscoveredSourceFile>,
)
