package iurii.bulanov.benchmark.evaluation.scanning

import iurii.bulanov.benchmark.evaluation.QualityFileMetrics
import iurii.bulanov.benchmark.evaluation.SourceStructure

/**
 * Scanner contract for source files that produce structural metrics.
 */
internal interface SourceStructureScanner {
    /**
     * Scans one source text into parser-backed structure metrics.
     */
    fun scan(source: String): SourceStructure
}

/**
 * Scanner contract for Kotlin source quality warning metrics.
 */
internal interface SourceQualityScanner {
    /**
     * Scans one generated Kotlin source file into quality warning metrics.
     */
    fun scan(
        path: String,
        source: String,
    ): QualityFileMetrics
}
