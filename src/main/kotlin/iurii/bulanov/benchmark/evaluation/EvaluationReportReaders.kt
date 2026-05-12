package iurii.bulanov.benchmark.evaluation

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Reads JSON reports produced by earlier pipeline stages for evaluator context.
 */
class EvaluationReportReaders {
    private val loader =
        Load(
            LoadSettings
                .builder()
                .setAllowDuplicateKeys(false)
                .build(),
        )

    /**
     * Reads checkout metadata, returning unavailable metadata when the report is absent.
     */
    fun readCheckout(path: Path): CheckoutEvaluation {
        if (!path.exists()) {
            return CheckoutEvaluation(
                available = false,
                buildStatus = "unavailable",
                javaFileCount = null,
                runBuild = null,
                benchmarkId = null,
            )
        }
        val root = loadMap(path)
        val counts = root.mapValue("counts")
        val benchmark = root.mapValue("benchmark")
        return CheckoutEvaluation(
            available = true,
            buildStatus = root.stringValue("build_status") ?: root.stringValue("buildStatus") ?: "unknown",
            javaFileCount = counts?.intValue("java_file_count") ?: root.intValue("java_file_count"),
            runBuild = root.booleanValue("run_build") ?: root.booleanValue("runBuild"),
            benchmarkId = benchmark?.stringValue("id"),
        )
    }

    /**
     * Reads J2K conversion metadata, returning unavailable metadata when the report is absent.
     */
    fun readConversion(path: Path): ConversionEvaluation {
        if (!path.exists()) {
            return ConversionEvaluation(
                available = false,
                status = "unavailable",
                sourceJavaFileCount = null,
                generatedKotlinFileCount = null,
                warningCount = 0,
                errorCount = 0,
                warnings = emptyList(),
                errors = emptyList(),
                benchmarkId = null,
            )
        }
        val root = loadMap(path)
        val counts = root.mapValue("counts")
        val benchmark = root.mapValue("benchmark")
        val warnings = root.stringList("warnings")
        val errors = root.stringList("errors")
        return ConversionEvaluation(
            available = true,
            status = root.stringValue("status") ?: "unknown",
            sourceJavaFileCount = counts?.intValue("source_java_file_count"),
            generatedKotlinFileCount = counts?.intValue("generated_kotlin_file_count"),
            warningCount = counts?.intValue("warning_count") ?: warnings.size,
            errorCount = counts?.intValue("error_count") ?: errors.size,
            warnings = warnings,
            errors = errors,
            benchmarkId = benchmark?.stringValue("id"),
        )
    }

    /**
     * Loads a JSON/YAML object from [path].
     */
    private fun loadMap(path: Path): Map<*, *> {
        val loaded =
            Files.newInputStream(path).use { input ->
                loader.loadFromInputStream(input)
            }
        return loaded as? Map<*, *> ?: error("report must be a JSON object: $path")
    }

    /**
     * Reads a nested map value by key.
     */
    private fun Map<*, *>.mapValue(key: String): Map<*, *>? = this[key] as? Map<*, *>

    /**
     * Reads a string value by key.
     */
    private fun Map<*, *>.stringValue(key: String): String? = this[key] as? String

    /**
     * Reads an integer value by key.
     */
    private fun Map<*, *>.intValue(key: String): Int? =
        when (val value = this[key]) {
            is Int -> value
            is Long -> value.toInt()
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }

    /**
     * Reads a boolean value by key.
     */
    private fun Map<*, *>.booleanValue(key: String): Boolean? =
        when (val value = this[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }

    /**
     * Reads a string list by key.
     */
    private fun Map<*, *>.stringList(key: String): List<String> =
        (this[key] as? List<*>)
            .orEmpty()
            .map { it.toString() }
}
