package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.benchmark.evaluation.scanning.JavaSourceScanner
import iurii.bulanov.benchmark.evaluation.scanning.KotlinPsiParser
import iurii.bulanov.benchmark.evaluation.scanning.KotlinQualityScanner
import iurii.bulanov.benchmark.evaluation.scanning.KotlinSourceScanner
import iurii.bulanov.benchmark.evaluation.scanning.SourceQualityScanner
import iurii.bulanov.benchmark.evaluation.scanning.SourceStructureScanner

/**
 * Extracts deterministic structural and quality signals from source text using language parsers.
 */
class SourceTextScanner {
    private val javaScanner: SourceStructureScanner = JavaSourceScanner()
    private val kotlinPsiParser = KotlinPsiParser()
    private val kotlinScanner: SourceStructureScanner = KotlinSourceScanner(kotlinPsiParser)
    private val kotlinQualityScanner: SourceQualityScanner = KotlinQualityScanner(kotlinPsiParser)

    /**
     * Scans Java source text for package, declarations, methods, and JavaBean accessor mappings.
     */
    fun scanJava(source: String): SourceStructure = javaScanner.scan(source)

    /**
     * Scans Kotlin source text for package, declarations, functions, and properties.
     */
    fun scanKotlin(source: String): SourceStructure = kotlinScanner.scan(source)

    /**
     * Scans Kotlin source text for mechanical conversion quality warnings.
     */
    fun scanKotlinQuality(
        path: String,
        source: String,
    ): QualityFileMetrics = kotlinQualityScanner.scan(path, source)
}
