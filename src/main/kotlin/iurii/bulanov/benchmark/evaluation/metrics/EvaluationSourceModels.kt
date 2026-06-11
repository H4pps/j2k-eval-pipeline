package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.SourceStructure
import iurii.bulanov.source.DiscoveredSourceFile

/**
 * Path lookups used by matched-file evaluation calculations.
 */
internal data class SourcePathIndex(
    val javaByExpectedPath: Map<String, DiscoveredSourceFile>,
    val kotlinByPath: Map<String, DiscoveredSourceFile>,
    val matchedPaths: List<String>,
    val missingKotlinFiles: List<String>,
    val unexpectedKotlinFiles: List<String>,
)

/**
 * Java/Kotlin parser structures for one matched generated file.
 */
internal data class MatchedSourceStructure(
    val path: String,
    val java: SourceStructure,
    val kotlin: SourceStructure,
)
