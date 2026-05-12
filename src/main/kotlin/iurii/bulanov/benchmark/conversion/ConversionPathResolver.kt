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
     * Resolves default conversion paths and applies optional CLI/Gradle overrides.
     */
    fun resolve(
        config: BenchmarkConfig,
        overrides: ConversionPathOverrides = ConversionPathOverrides(null, null, null, null),
    ): ConversionPaths {
        val benchmarkRoot = outputRoot.resolve(config.id)
        val stagingDirectory = safeBuildPath(overrides.stagingDirectory ?: benchmarkRoot.resolve("staging-source"), "stagingDir")
        val generatedKotlinDirectory =
            safeBuildPath(overrides.generatedKotlinDirectory ?: benchmarkRoot.resolve("generated-kotlin"), "generatedKotlinDir")
        val conversionReport = safeBuildPath(overrides.conversionReport ?: benchmarkRoot.resolve("conversion.json"), "conversionReport")
        val logsDirectory = safeBuildPath(overrides.logsDirectory ?: benchmarkRoot.resolve("logs"), "logsDir")

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
}
