package iurii.bulanov.j2k.runner

import iurii.bulanov.benchmark.conversion.ConversionPathOverrides
import iurii.bulanov.benchmark.conversion.ConverterKind
import java.nio.file.Path

/**
 * Parsed command-line options for the headless J2K runner.
 */
data class J2kRunnerOptions(
    val configPath: Path,
    val kind: ConverterKind,
    val stagingDirectory: Path?,
    val generatedKotlinDirectory: Path?,
    val conversionReport: Path?,
    val logsDirectory: Path?,
) {
    /**
     * Converts parsed options into conversion path overrides.
     */
    fun toPathOverrides(): ConversionPathOverrides =
        ConversionPathOverrides(
            stagingDirectory = stagingDirectory,
            generatedKotlinDirectory = generatedKotlinDirectory,
            conversionReport = conversionReport,
            logsDirectory = logsDirectory,
        )

    /**
     * Resolves the harness repository root from the benchmark config location.
     */
    fun harnessRoot(): Path {
        val absoluteConfigPath = configPath.toAbsolutePath().normalize()
        val configDirectory = absoluteConfigPath.parent ?: Path.of("").toAbsolutePath().normalize()
        return if (configDirectory.fileName?.toString() == "benchmarks") {
            configDirectory.parent ?: configDirectory
        } else {
            configDirectory
        }
    }

    companion object {
        /**
         * Parses runner arguments after the `j2k-convert` command token.
         */
        fun parse(args: List<String>): J2kRunnerOptions {
            val values = mutableMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val option = args[index]
                if (!option.startsWith("--")) {
                    throw IllegalArgumentException("unexpected argument: $option")
                }
                val value = args.getOrNull(index + 1) ?: throw IllegalArgumentException("missing value for $option")
                values[option] = value
                index += 2
            }

            val config = values.remove("--config") ?: throw IllegalArgumentException("missing required --config")
            val kind = values.remove("--kind")?.let { ConverterKind.fromId(it) } ?: ConverterKind.K1_OLD_DUMB
            val stagingDirectory = values.remove("--staging-dir")?.let { Path.of(it) }
            val generatedKotlinDirectory = values.remove("--generated-kotlin-dir")?.let { Path.of(it) }
            val conversionReport = values.remove("--conversion-report")?.let { Path.of(it) }
            val logsDirectory = values.remove("--logs-dir")?.let { Path.of(it) }
            if (values.isNotEmpty()) {
                throw IllegalArgumentException("unknown options: ${values.keys.sorted().joinToString(", ")}")
            }

            return J2kRunnerOptions(
                configPath = Path.of(config),
                kind = kind,
                stagingDirectory = stagingDirectory,
                generatedKotlinDirectory = generatedKotlinDirectory,
                conversionReport = conversionReport,
                logsDirectory = logsDirectory,
            )
        }
    }
}
