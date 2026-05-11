package iurii.bulanov.benchmark.source

import iurii.bulanov.benchmark.checkout.CheckoutException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Validates Java source roots and counts Java files under a benchmark checkout.
 */
class JavaSourceCounter {
    /**
     * Resolves configured source roots, verifies they exist, and counts Java files recursively.
     */
    fun countJavaFiles(checkoutDirectory: Path, sourceRoots: List<String>): Int {
        val missingRoots = mutableListOf<String>()
        var javaFileCount = 0

        sourceRoots.forEach { sourceRoot ->
            val rootPath = resolveRelativeDirectory(checkoutDirectory, sourceRoot, "java.sourceRoots")
            if (!rootPath.isDirectory()) {
                missingRoots += sourceRoot
            } else {
                javaFileCount += countJavaFilesUnderRoot(rootPath)
            }
        }

        if (missingRoots.isNotEmpty()) {
            throw CheckoutException("missing Java source roots: ${missingRoots.joinToString(", ")}")
        }
        if (javaFileCount == 0) {
            throw CheckoutException("no Java files found under configured source roots")
        }

        return javaFileCount
    }

    /**
     * Counts `.java` files recursively under an existing source root.
     */
    private fun countJavaFilesUnderRoot(sourceRootPath: Path): Int =
        Files.walk(sourceRootPath).use { stream ->
            stream.asSequence().count { Files.isRegularFile(it) && it.name.endsWith(".java") }
        }

    /**
     * Resolves and validates a relative directory under [checkoutDirectory].
     */
    private fun resolveRelativeDirectory(checkoutDirectory: Path, relativePath: String, fieldName: String): Path {
        val path = Paths.get(relativePath)
        if (path.isAbsolute) {
            throw CheckoutException("$fieldName entries must be relative paths: $relativePath")
        }
        val resolved = checkoutDirectory.resolve(path).normalize()
        if (!resolved.startsWith(checkoutDirectory.normalize())) {
            throw CheckoutException("$fieldName entry escapes checkout directory: $relativePath")
        }
        return resolved
    }
}
