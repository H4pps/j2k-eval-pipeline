package iurii.bulanov.j2k.runner.engine.k2

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.AbstractExtensionConversionEngine

/**
 * New IR-pipeline converter on the K2 frontend (`K2`), run with its diagnostic-based
 * `K2J2KPostProcessor`. Requires the IDE launched with `idea.kotlin.plugin.use.k2=true`.
 *
 * K2 post-processing is diagnostic-based and benefits from a resolvable classpath.
 * 
 * Note: K2 has a known issue where KtFiles are created without import list nodes,
 * causing K2ShortenReferenceProcessing to fail when trying to add imports. This results
 * in fully-qualified references throughout the generated code. This is a limitation of
 * the K2 converter itself and cannot be fixed from the harness side without modifying
 * IntelliJ's J2K converter internals.
 */
class K2ConversionEngine : AbstractExtensionConversionEngine(ConverterKind.K2)
