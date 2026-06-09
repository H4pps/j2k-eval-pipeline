package iurii.bulanov.benchmark.comparison

import iurii.bulanov.benchmark.conversion.ConverterKind

/**
 * One kind's evaluation, parsed from its `evaluation.json`.
 *
 * Backed by the raw parsed JSON object so the comparison writer can pull any metric by
 * `(section, key)` without this class enumerating every field. Only present kinds get a
 * [KindEvaluation]; absent reports are skipped by the reader.
 */
class KindEvaluation(
    val kind: ConverterKind,
    private val root: Map<*, *>,
) {
    /** Evaluator status (`completed` / `completed_with_warnings`). */
    val evaluationStatus: String = root.stringValue("status") ?: "unknown"

    /** Total evaluator warning count (`counts.warning_count`). */
    val warningCount: Int = root.mapValue("counts")?.intValue("warning_count") ?: 0

    /** Generated-Kotlin files the converter failed to produce for a Java input. */
    val missingKotlinFiles: List<String> = root.mapValue("file_coverage")?.stringList("missing_kotlin_files") ?: emptyList()

    /** Source-level converter failures recorded for this kind. */
    val conversionErrors: List<String> = root.mapValue("conversion")?.stringList("errors") ?: emptyList()

    /** Reads an integer metric from `<section>.<key>`, or null if absent. */
    fun int(
        section: String,
        key: String,
    ): Int? = root.mapValue(section)?.intValue(key)

    /** Reads a numeric (possibly fractional) metric from `<section>.<key>`, or null if absent. */
    fun number(
        section: String,
        key: String,
    ): Double? = root.mapValue(section)?.doubleValue(key)

    /** Reads a string metric from `<section>.<key>`, or null if absent. */
    fun text(
        section: String,
        key: String,
    ): String? = root.mapValue(section)?.stringValue(key)

    /** Reads a string-list metric from `<section>.<key>`. */
    fun strings(
        section: String,
        key: String,
    ): List<String> = root.mapValue(section)?.stringList(key) ?: emptyList()

    /** Size of a string-list metric at `<section>.<key>`. */
    fun listSize(
        section: String,
        key: String,
    ): Int = strings(section, key).size
}

/**
 * Aggregated comparison across the converter kinds present for one benchmark.
 */
data class Comparison(
    val benchmarkId: String,
    val benchmarkName: String,
    val kinds: List<KindEvaluation>,
    val missingKinds: List<ConverterKind>,
)

/** Reads a nested map value by key. */
internal fun Map<*, *>.mapValue(key: String): Map<*, *>? = this[key] as? Map<*, *>

/** Reads a string value by key. */
internal fun Map<*, *>.stringValue(key: String): String? = this[key] as? String

/** Reads an integer value by key, coercing numeric/string forms. */
internal fun Map<*, *>.intValue(key: String): Int? =
    when (val value = this[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

/** Reads a double value by key, coercing numeric/string forms. */
internal fun Map<*, *>.doubleValue(key: String): Double? =
    when (val value = this[key]) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

/** Reads a string list by key. */
internal fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>).orEmpty().map { it.toString() }
