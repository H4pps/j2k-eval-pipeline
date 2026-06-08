package iurii.bulanov.j2k.runner.engine

/**
 * Selects the static J2K conversion engine for a run.
 *
 * Only the old (K1) converter exists today. To add the new (K2) converter:
 * 1. add `K2NewJ2kConversionEngine : J2kConversionEngine`,
 * 2. branch in [create] on an engine selector,
 * 3. add `--engine` to `J2kRunnerOptions` and map it to `-Didea.kotlin.plugin.use.k2`
 *    in `j2k-runner-plugin/build.gradle.kts`.
 */
class J2kConversionEngineFactory {
    /**
     * Returns the conversion engine to use. Currently, always the old (K1) converter.
     */
    fun create(): J2kConversionEngine = OldJ2kConversionEngine()
}
