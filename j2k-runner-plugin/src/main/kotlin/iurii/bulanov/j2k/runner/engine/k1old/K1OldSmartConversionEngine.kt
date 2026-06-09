package iurii.bulanov.j2k.runner.engine.k1old

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.AbstractExtensionConversionEngine

/**
 * Legacy (`K1_OLD`) converter run against an indexed project (smart mode).
 *
 * Unlike [K1OldDumbConversionEngine] it neither suppresses inferred annotations nor forces dumb
 * mode: the runner indexes the project first (`requiresSmartMode = true`, the base default), so the
 * old converter resolves against real indexes. This is the indexed counterpart of the dumb baseline,
 * letting the comparison measure how much resolution helps the legacy converter.
 */
class K1OldSmartConversionEngine : AbstractExtensionConversionEngine(ConverterKind.K1_OLD_SMART)
