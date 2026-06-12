package iurii.bulanov.j2k.runner.engine

import iurii.bulanov.benchmark.conversion.ConverterKind
import iurii.bulanov.j2k.runner.engine.k1new.K1NewConversionEngine
import iurii.bulanov.j2k.runner.engine.k1old.K1OldDumbConversionEngine
import iurii.bulanov.j2k.runner.engine.k1old.K1OldSmartConversionEngine
import iurii.bulanov.j2k.runner.engine.k2.K2ConversionEngine

/**
 * Selects the conversion engine for a [ConverterKind]. Each kind has its own engine in a
 * dedicated subpackage; to add another converter, add a kind + engine and a branch here.
 */
class J2kConversionEngineFactory {
    /**
     * Returns the engine implementing [kind].
     */
    fun create(kind: ConverterKind): J2kConversionEngine =
        when (kind) {
            ConverterKind.K1_OLD_DUMB -> K1OldDumbConversionEngine()
            ConverterKind.K1_OLD_SMART -> K1OldSmartConversionEngine()
            ConverterKind.K1_NEW -> K1NewConversionEngine()
            ConverterKind.K2 -> K2ConversionEngine()
        }
}
