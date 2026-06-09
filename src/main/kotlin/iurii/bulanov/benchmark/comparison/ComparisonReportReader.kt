package iurii.bulanov.benchmark.comparison

import iurii.bulanov.benchmark.conversion.ConverterKind
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Reads per-kind `evaluation.json` reports for a benchmark so they can be compared.
 *
 * Each kind's report is expected at `<reportBaseDirectory>/<kind.id>/evaluation.json` (the layout
 * `EvaluationPaths` writes). Kinds without a report are reported as missing and skipped, so a
 * partial run (e.g. one converter failed in CI) still yields a comparison of the others.
 */
class ComparisonReportReader {
    private val loader =
        Load(
            LoadSettings
                .builder()
                .setAllowDuplicateKeys(false)
                .build(),
        )

    /**
     * Reads the [kinds] present under [reportBaseDirectory], returning the parsed evaluations and
     * the kinds whose report was absent.
     */
    fun read(
        reportBaseDirectory: Path,
        kinds: List<ConverterKind>,
    ): ReadResult {
        val present = mutableListOf<KindEvaluation>()
        val missing = mutableListOf<ConverterKind>()
        kinds.forEach { kind ->
            val reportPath = reportBaseDirectory.resolve(kind.id).resolve("evaluation.json")
            if (reportPath.exists()) {
                present += KindEvaluation(kind, loadMap(reportPath))
            } else {
                missing += kind
            }
        }
        return ReadResult(present, missing)
    }

    /** Loads a JSON/YAML object from [path]. */
    private fun loadMap(path: Path): Map<*, *> {
        val loaded =
            Files.newInputStream(path).use { input ->
                loader.loadFromInputStream(input)
            }
        return loaded as? Map<*, *> ?: error("evaluation report must be a JSON object: $path")
    }
}

/**
 * Outcome of reading per-kind reports: the present evaluations and the kinds that were absent.
 */
data class ReadResult(
    val present: List<KindEvaluation>,
    val missing: List<ConverterKind>,
)
