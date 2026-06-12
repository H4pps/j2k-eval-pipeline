package iurii.bulanov.benchmark.conversion

import iurii.bulanov.benchmark.config.BenchmarkConfig
import java.nio.file.Path

/**
 * Resolves and validates output paths used by static J2K conversion.
 */
class ConversionPathResolver(
    private val outputRoot: Path = Path.of("build", "j2k"),
) {
    /**
     * Resolves default conversion paths for [kind] and applies optional CLI/Gradle overrides.
     *
     * Each converter kind writes to its own `<id>/<kind>/...` subtree so the kinds do not
     * overwrite each other; the staging source tree stays shared per benchmark because it is
     * read-only converter input identical for every kind.
     */
    fun resolve(
        config: BenchmarkConfig,
        kind: ConverterKind,
        overrides: ConversionPathOverrides = ConversionPathOverrides(null, null, null, null),
    ): ConversionPaths {
        val benchmarkRoot = outputRoot.resolve(config.id)
        val kindRoot = benchmarkRoot.resolve(kind.id)
        val stagingDirectory =
            safeBuildPath(
                overrides.stagingDirectory ?: benchmarkRoot.resolve("staging-source-${config.repository.ref.toPathToken()}"),
                "stagingDir",
            )
        val generatedKotlinDirectory =
            safeBuildPath(overrides.generatedKotlinDirectory ?: kindRoot.resolve("generated-kotlin"), "generatedKotlinDir")
        val conversionReport = safeBuildPath(overrides.conversionReport ?: kindRoot.resolve("conversion.json"), "conversionReport")
        val logsDirectory = safeBuildPath(overrides.logsDirectory ?: kindRoot.resolve("logs"), "logsDir")

        return ConversionPaths(
            stagingDirectory = stagingDirectory,
            generatedKotlinDirectory = generatedKotlinDirectory,
            conversionReport = conversionReport,
            logsDirectory = logsDirectory,
        )
    }

    /**
     * Validates that conversion outputs stay in the ignored `build/` tree.
     */
    private fun safeBuildPath(
        path: Path,
        fieldName: String,
    ): Path {
        if (path.isAbsolute) {
            throw ConversionException("$fieldName must be a relative path under build/: $path")
        }
        if (path.any { it.toString() == ".." }) {
            throw ConversionException("$fieldName must not contain '..': $path")
        }
        val normalized = path.normalize()
        if (!normalized.startsWith(Path.of("build"))) {
            throw ConversionException("$fieldName must stay under build/: $path")
        }
        return normalized
    }

    /**
     * Produces a compact path segment from a repository ref for disposable staging directories.
     */
    private fun String.toPathToken(): String =
        take(12)
            .map { character -> if (character.isSafePathTokenCharacter()) character else '_' }
            .joinToString("")
            .ifBlank { "ref" }

    /**
     * Returns true when a character can be used inside a simple build-path token.
     */
    private fun Char.isSafePathTokenCharacter(): Boolean = isLetterOrDigit() || this in SAFE_PATH_TOKEN_PUNCTUATION

    private companion object {
        private val SAFE_PATH_TOKEN_PUNCTUATION = setOf('.', '_', '-')
    }
}
