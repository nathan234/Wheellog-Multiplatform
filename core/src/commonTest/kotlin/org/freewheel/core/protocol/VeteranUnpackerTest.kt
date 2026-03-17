package org.freewheel.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [VeteranUnpacker] error counters.
 *
 * Veteran frame format:
 * - Bytes 0-2: Header (DC 5A 5C)
 * - Byte 3: Length
 * - Bytes 4+: Data payload
 * - Last 4 bytes: CRC32 (for newer firmware, len > 38)
 *
 * Data verification checks at byte positions 22, 23, and 30 reject
 * frames with unexpected values.
 */
class VeteranUnpackerTest {

    private fun feedBytes(unpacker: VeteranUnpacker, bytes: ByteArray): Boolean {
        var completed = false
        for (b in bytes) {
            if (unpacker.addChar(b.toInt() and 0xFF)) {
                completed = true
            }
        }
        return completed
    }

    /**
     * Build a valid Veteran frame with the given payload length.
     * The header (DC 5A 5C) and length byte are prepended.
     * Byte 22 = 0x00, byte 23 = 0x00, byte 30 = 0x00 to pass validation.
     */
    private fun buildValidFrame(payloadLen: Int = 36): ByteArray {
        val frame = ByteArray(payloadLen + 4) // header(3) + len(1) + payload
        frame[0] = 0xDC.toByte()
        frame[1] = 0x5A.toByte()
        frame[2] = 0x5C.toByte()
        frame[3] = payloadLen.toByte()
        // Verification bytes must pass checks:
        // bsize 22 → byte == 0x00 (frame[22] = 0x00, already zeroed)
        // bsize 23 → (byte and 0xFE) == 0x00 (frame[23] = 0x00, already zeroed)
        // bsize 30 → byte == 0x00 || byte == 0x07 (frame[30] = 0x00, already zeroed)
        return frame
    }

    @Test
    fun stats_initiallyZero() {
        val unpacker = VeteranUnpacker()
        assertEquals(0, unpacker.stats.errorResets)
        assertEquals(0, unpacker.stats.bytesDiscarded)
    }

    @Test
    fun stats_validFrame_doesNotIncrement() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        feedBytes(unpacker, frame)
        assertEquals(0, unpacker.stats.errorResets)
        assertEquals(0, unpacker.stats.bytesDiscarded)
    }

    @Test
    fun stats_dataValidationFailure_incrementsCounters() {
        val unpacker = VeteranUnpacker()
        // Build a frame where byte at position 22 is non-zero (fails validation)
        val frame = buildValidFrame(36)
        frame[22] = 0x01.toByte() // should be 0x00 → validation failure

        val result = feedBytes(unpacker, frame)
        assertFalse(result, "Frame with invalid byte 22 should be rejected")
        assertEquals(1, unpacker.stats.errorResets)
        assertTrue(unpacker.stats.bytesDiscarded > 0)
    }

    @Test
    fun stats_byte23ValidationFailure_incrementsCounters() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        frame[23] = 0x04.toByte() // (0x04 and 0xFE) = 0x04 ≠ 0x00 → failure

        feedBytes(unpacker, frame)
        assertEquals(1, unpacker.stats.errorResets)
    }

    @Test
    fun stats_byte30ValidationFailure_incrementsCounters() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        frame[30] = 0x05.toByte() // not 0x00 or 0x07 → failure

        feedBytes(unpacker, frame)
        assertEquals(1, unpacker.stats.errorResets)
    }

    @Test
    fun stats_crcMismatch_incrementsCounters() {
        val unpacker = VeteranUnpacker()
        // Build a frame with len > 38 to trigger CRC check, but wrong CRC
        val payloadLen = 42
        val frame = buildValidFrame(payloadLen)
        // CRC bytes are the last 4 bytes of payload — leave as zeros (wrong CRC)

        val result = feedBytes(unpacker, frame)
        assertFalse(result, "Frame with bad CRC should be rejected")
        assertEquals(1, unpacker.stats.errorResets)
        assertTrue(unpacker.stats.bytesDiscarded > 0)
    }

    @Test
    fun stats_persistAcrossReset() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        frame[22] = 0x01.toByte() // trigger validation error

        feedBytes(unpacker, frame)
        unpacker.reset()

        assertEquals(1, unpacker.stats.errorResets, "Stats should persist across reset()")
    }

    @Test
    fun stats_clearedByResetStats() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        frame[22] = 0x01.toByte()

        feedBytes(unpacker, frame)
        unpacker.resetStats()

        assertEquals(0, unpacker.stats.errorResets)
        assertEquals(0, unpacker.stats.bytesDiscarded)
    }

    @Test
    fun stats_multipleErrors_accumulate() {
        val unpacker = VeteranUnpacker()
        val frame = buildValidFrame(36)
        frame[22] = 0x01.toByte() // validation error

        feedBytes(unpacker, frame)
        feedBytes(unpacker, frame)

        assertEquals(2, unpacker.stats.errorResets)
    }
}
