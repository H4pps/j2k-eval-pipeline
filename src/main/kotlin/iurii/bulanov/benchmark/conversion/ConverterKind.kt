package iurii.bulanov.benchmark.conversion

/**
 * One run configuration of an IntelliJ Java-to-Kotlin converter.
 *
 * Maps the harness-facing id to the IntelliJ `J2kConverterExtension.Kind` name and the
 * Kotlin-plugin mode required to run it. `K2` needs the IDE launched with
 * `idea.kotlin.plugin.use.k2=true`; the K1 variants need it `false`.
 *
 * The legacy converter (`K1_OLD`) is the only one that can run without indexes, so it is exposed
 * as two configurations: [K1_OLD_DUMB] (isolated, dumb mode) and [K1_OLD_SMART] (indexed). The new
 * converters resolve through the Analysis API and therefore only exist as indexed (smart) kinds.
 */
enum class ConverterKind(
    val id: String,
    val platformKind: String,
    val useK2: Boolean,
) {
    /** Legacy converter (`OldJavaToKotlinConverter`) in dumb mode — no indexing, isolated baseline. */
    K1_OLD_DUMB("k1-old-dumb", "K1_OLD", useK2 = false),

    /** Legacy converter, run against an indexed project (smart mode) for resolution. */
    K1_OLD_SMART("k1-old-smart", "K1_OLD", useK2 = false),

    /** New IR pipeline (`NewJavaToKotlinConverter`) on the K1 frontend. */
    K1_NEW("k1-new", "K1_NEW", useK2 = false),

    /** New IR pipeline on the K2 frontend, with diagnostic-based post-processing. */
    K2("k2", "K2", useK2 = true),
    ;

    companion object {
        /**
         * Resolves a [ConverterKind] from its harness id, e.g. `k1-new`.
         */
        fun fromId(id: String): ConverterKind =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException(
                    "unknown converter kind: $id (expected one of ${entries.joinToString(", ") { it.id }})",
                )
    }
}
