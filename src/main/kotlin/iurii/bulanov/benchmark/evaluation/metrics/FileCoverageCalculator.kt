package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.FileCoverageMetrics
import iurii.bulanov.benchmark.evaluation.SourceStructure
import iurii.bulanov.benchmark.evaluation.SourceTextScanner
import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Calculates matched-file, empty-file, and package-preservation metrics.
 */
internal class FileCoverageCalculator(
    private val scanner: SourceTextScanner,
) {
    /**
     * Calculates file coverage and package preservation metrics.
     */
    fun calculate(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        pathIndex: SourcePathIndex,
    ): FileCoverageMetrics {
        val packageMismatches = packageMismatches(pathIndex)
        return FileCoverageMetrics(
            javaFileCount = javaFiles.size,
            kotlinFileCount = kotlinFiles.size,
            matchedKotlinFileCount = pathIndex.matchedPaths.size,
            missingKotlinFiles = pathIndex.missingKotlinFiles,
            unexpectedKotlinFiles = pathIndex.unexpectedKotlinFiles,
            emptyGeneratedFiles = emptyGeneratedFiles(kotlinFiles),
            packagePreservedCount = pathIndex.matchedPaths.size - packageMismatches.size,
            packageMismatchFiles = packageMismatches,
        )
    }

    private fun packageMismatches(pathIndex: SourcePathIndex): List<String> =
        pathIndex.matchedPaths.filterNot { relativeKotlinPath ->
            val javaStructure =
                scanner.scanJava(
                    pathIndex.javaByExpectedPath
                        .getValue(relativeKotlinPath)
                        .absolutePath
                        .readText(),
                )
            val kotlinStructure =
                scanner.scanKotlin(
                    pathIndex.kotlinByPath
                        .getValue(relativeKotlinPath)
                        .absolutePath
                        .readText(),
                )
            packageIsPreserved(relativeKotlinPath, javaStructure, kotlinStructure)
        }

    private fun packageIsPreserved(
        relativeKotlinPath: String,
        javaStructure: SourceStructure,
        kotlinStructure: SourceStructure,
    ): Boolean {
        val expectedPackagePath = javaStructure.packageName?.replace('.', '/')
        val actualPackagePath = Path.of(relativeKotlinPath).parent?.toString()
        return javaStructure.packageName == kotlinStructure.packageName &&
            (expectedPackagePath == null || actualPackagePath == expectedPackagePath)
    }

    private fun emptyGeneratedFiles(kotlinFiles: List<DiscoveredSourceFile>): List<String> =
        kotlinFiles
            .filter { it.absolutePath.readText().isBlank() }
            .map { it.relativePath.normalize().toString() }
            .sorted()
}
