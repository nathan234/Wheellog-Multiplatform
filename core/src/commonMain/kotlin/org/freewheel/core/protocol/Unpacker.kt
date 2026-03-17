package org.freewheel.core.protocol

/**
 * Cumulative error counters for an [Unpacker].
 *
 * Tracks frames silently dropped by the unpacker before they ever reach the decoder.
 * This distinguishes "unpacker dropped N frames" from "decoder saw N unhandled frames"
 * — very different root causes.
 *
 * Counters accumulate across [Unpacker.reset] calls (which happen after every
 * successfully extracted frame). Only [Unpacker.resetStats] clears them.
 *
 * @param errorResets Number of times the unpacker abandoned a partial frame due to
 *   invalid data (e.g., bad footer, packet overflow, CRC mismatch).
 * @param bytesDiscarded Total bytes consumed in abandoned partial frames.
 */
data class UnpackerStats(
    val errorResets: Int = 0,
    val bytesDiscarded: Int = 0
)

/**
 * Interface for frame unpackers that reassemble BLE notifications into complete protocol frames.
 *
 * All unpackers follow a state machine pattern:
 * 1. Feed bytes one at a time via [addChar]
 * 2. When a complete frame is detected, [addChar] returns true
 * 3. Retrieve the frame via [getBuffer]
 * 4. Call [reset] to prepare for the next frame
 */
internal interface Unpacker {
    /**
     * Add a byte to the unpacker.
     * @param c The byte value (0-255)
     * @return true if a complete valid frame is ready for retrieval via [getBuffer]
     */
    fun addChar(c: Int): Boolean

    /**
     * Get the complete frame buffer.
     * Only valid after [addChar] returns true.
     */
    fun getBuffer(): ByteArray

    /**
     * Reset the unpacker state, ready to receive a new frame.
     * Does NOT reset error counters — use [resetStats] for that.
     */
    fun reset()

    /**
     * Cumulative error counters since last [resetStats] call.
     * Persists across [reset] calls so session-level totals are available.
     */
    val stats: UnpackerStats get() = UnpackerStats()

    /**
     * Clear accumulated error counters.
     * Called when starting a new session or when a consumer has captured the stats.
     */
    fun resetStats() {}
}
