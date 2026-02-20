package com.cooper.wheellog.kmp

/**
 * Decoder mode for selecting which decoder implementation to use.
 *
 * This setting allows users to choose between:
 * - Legacy decoders (original Java/Kotlin implementation)
 * - KMP decoders (new Kotlin Multiplatform implementation)
 */
enum class DecoderMode(val value: Int) {
    LEGACY_ONLY(0),
    KMP_ONLY(1);

    companion object {
        fun fromInt(value: Int): DecoderMode {
            return entries.find { it.value == value } ?: LEGACY_ONLY
        }
    }
}
