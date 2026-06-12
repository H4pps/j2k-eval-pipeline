package iurii.bulanov.j2k.runner.engine.k2

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.AbstractExtensionConversionEngine

/**
 * New IR-pipeline converter on the K2 frontend (`K2`), run with its diagnostic-based
 * `K2J2KPostProcessor`. Requires the IDE launched with `idea.kotlin.plugin.use.k2=true`.
 *
 * Home for any K2-specific project/resolution handling discovered during the smoke test;
 * K2 post-processing is diagnostic-based and benefits from a resolvable classpath.
 */
class K2ConversionEngine : AbstractExtensionConversionEngine(ConverterKind.K2)
