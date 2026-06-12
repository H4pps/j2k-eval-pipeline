package iurii.bulanov.j2k.runner.engine.k1new

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.AbstractExtensionConversionEngine

/**
 * New IR-pipeline converter on the K1 frontend (`K1_NEW`), run with its `NewJ2kPostProcessor`.
 *
 * Starts without the `K1_OLD` inferred-annotation workaround; revisit if the headless run hits
 * index-readiness issues.
 */
class K1NewConversionEngine : AbstractExtensionConversionEngine(ConverterKind.K1_NEW)
