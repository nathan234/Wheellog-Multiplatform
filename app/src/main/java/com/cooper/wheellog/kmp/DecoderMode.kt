package com.cooper.wheellog.kmp

/**
 * Decoder mode for selecting which decoder implementation to use.
 *
 * This setting allows users to choose between:
 * - Legacy decoders (original Java/Kotlin implementation)
 * - KMP decoders (new Kotlin Multiplatform implementation)
 * - Both (for comparison/validation during migration)
 */
enum class DecoderMode(val value: Int) {
    /**
     * Use only the legacy Java/Kotlin decoders.
     * This is the original, battle-tested implementation.
     */
    LEGACY_ONLY(0),

    /**
     * Use only the new KMP decoders.
     * This is the cross-platform implementation for Android/iOS.
     */
    KMP_ONLY(1),

    /**
     * Use both decoders in parallel.
     * Legacy decoder updates WheelData, KMP decoder runs for validation.
     * Useful for testing and comparing decoder outputs.
     */
    BOTH(2);

    companion object {
        fun fromInt(value: Int): DecoderMode {
            return entries.find { it.value == value } ?: LEGACY_ONLY
        }
    }
}
