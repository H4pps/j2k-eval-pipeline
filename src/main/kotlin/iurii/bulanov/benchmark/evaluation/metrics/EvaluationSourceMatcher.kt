package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.SourceTextScanner
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Builds expected generated Kotlin paths and scans matched Java/Kotlin file pairs.
 */
internal class EvaluationSourceMatcher(
    private val scanner: SourceTextScanner,
) {
    /**
     * Maps a checkout-relative Java path to the generated-output-relative Kotlin path.
     */
    fun expectedKotlinRelativePath(
        javaRelativePath: Path,
        sourceRoots: List<String>,
    ): Path {
        val normalizedJavaPath = javaRelativePath.normalize()
        val sourceRootRelativePath =
            sourceRoots
                .map { Path.of(it).normalize() }
                .firstOrNull { normalizedJavaPath.startsWith(it) }
                ?.relativize(normalizedJavaPath)
                ?: normalizedJavaPath
        val kotlinFileName = "${sourceRootRelativePath.nameWithoutExtension}.kt"
        return sourceRootRelativePath.parent?.resolve(kotlinFileName)?.normalize() ?: Path.of(kotlinFileName)
    }

    /**
     * Builds lookup tables for expected generated Kotlin paths.
     */
    fun sourcePathIndex(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        sourceRoots: List<String>,
    ): SourcePathIndex {
        val javaByExpectedPath = javaFiles.associateBy { expectedKotlinRelativePath(it.relativePath, sourceRoots).toString() }
        val kotlinByPath = kotlinFiles.associateBy { it.relativePath.normalize().toString() }
        return SourcePathIndex(
            javaByExpectedPath = javaByExpectedPath,
            kotlinByPath = kotlinByPath,
            matchedPaths = javaByExpectedPath.keys.intersect(kotlinByPath.keys).sorted(),
            missingKotlinFiles = javaByExpectedPath.keys.minus(kotlinByPath.keys).sorted(),
            unexpectedKotlinFiles = kotlinByPath.keys.minus(javaByExpectedPath.keys).sorted(),
        )
    }

    /**
     * Reads and scans all matched Java/Kotlin file pairs.
     */
    fun matchedStructures(pathIndex: SourcePathIndex): List<MatchedSourceStructure> =
        pathIndex.matchedPaths.map { relativeKotlinPath ->
            MatchedSourceStructure(
                path = relativeKotlinPath,
                java =
                    scanner.scanJava(
                        pathIndex.javaByExpectedPath
                            .getValue(relativeKotlinPath)
                            .absolutePath
                            .readText(),
                    ),
                kotlin =
                    scanner.scanKotlin(
                        pathIndex.kotlinByPath
                            .getValue(relativeKotlinPath)
                            .absolutePath
                            .readText(),
                    ),
            )
        }
}
