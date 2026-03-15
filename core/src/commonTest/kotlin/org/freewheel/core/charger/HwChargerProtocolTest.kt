package org.freewheel.core.charger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HwChargerProtocolTest {

    // ── Frame building ─────────────────────────────────────────────

    @Test
    fun buildFrame_simpleCommand_correctFormat() {
        // ACK-style command: no payload
        val frame = HwChargerProtocol.buildFrame(0x06)
        // [size=2, command=6, checksum]
        assertEquals(3, frame.size)
        assertEquals(2, frame[0].toInt() and 0xFF) // size = 2 (cmd + checksum)
        assertEquals(6, frame[1].toInt() and 0xFF) // command
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    @Test
    fun buildFrame_withPayload_correctFormat() {
        val payload = byteArrayOf(0x01, 0x00, 0x00, 0x00)
        val frame = HwChargerProtocol.buildFrame(0x0C, payload)
        // [size=6, command=0x0C, payload(4), checksum]
        assertEquals(7, frame.size)
        assertEquals(6, frame[0].toInt() and 0xFF) // size = 4 + 2
        assertEquals(0x0C, frame[1].toInt() and 0xFF)
        assertEquals(0x01, frame[2].toInt() and 0xFF) // first payload byte
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    // ── Checksum ───────────────────────────────────────────────────

    @Test
    fun checksum_matchesRogerAlgorithm() {
        // From Roger app w0.a.g(): sum bytes[1] through bytes[len-2], AND 0xFF
        // Frame: [48, 6, <payload>, checksum] — 49 bytes total
        // Construct a simple frame and verify
        val frame = byteArrayOf(3, 2, 1, 0) // size=3, cmd=2, payload=1, checksum=?
        val expected = (2 + 1) and 0xFF // bytes[1] + bytes[2] = 3
        assertEquals(expected.toByte(), HwChargerProtocol.checksum(frame))
    }

    @Test
    fun checksum_ackResponse_matchesMockData() {
        // From Roger mock g.java: ACK = {3, cmd, 1, checksum}
        // checksum = (cmd + 1) & 0xFF
        val cmd: Byte = 7
        val frame = byteArrayOf(3, cmd, 1, 0) // last byte placeholder
        val checksum = HwChargerProtocol.checksum(frame)
        assertEquals(((7 + 1) and 0xFF).toByte(), checksum)
    }

    @Test
    fun isChecksumValid_validFrame_returnsTrue() {
        // Auth success response from Roger mock: {3, 2, 1, 3}
        val frame = byteArrayOf(3, 2, 1, 3)
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    @Test
    fun isChecksumValid_corruptFrame_returnsFalse() {
        val frame = byteArrayOf(3, 2, 1, 99) // wrong checksum
        assertFalse(HwChargerProtocol.isChecksumValid(frame))
    }

    @Test
    fun isChecksumValid_tooShort_returnsFalse() {
        assertFalse(HwChargerProtocol.isChecksumValid(byteArrayOf(1, 2)))
    }

    // ── Float encoding/decoding ────────────────────────────────────

    @Test
    fun encodeFloat_decodeFloat_roundTrip() {
        val values = listOf(0f, 48.5f, 5.2f, -1.0f, 230.5f, 0.001f)
        for (v in values) {
            val encoded = HwChargerProtocol.encodeFloat(v)
            assertEquals(4, encoded.size)
            val decoded = HwChargerProtocol.decodeFloat(encoded, 0)
            assertEquals(v, decoded, "Round-trip failed for $v")
        }
    }

    @Test
    fun decodeFloat_knownBytes_correctValue() {
        // 48.5f in LE IEEE 754: Float.toRawBits(48.5f) = 0x42420000
        // LE bytes: 0x00, 0x00, 0x42, 0x42
        val bits = 48.5f.toRawBits()
        val bytes = byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte()
        )
        assertEquals(48.5f, HwChargerProtocol.decodeFloat(bytes, 0))
    }

    @Test
    fun decodeFloat_withOffset_correctValue() {
        val prefix = byteArrayOf(0x00, 0x00) // 2-byte prefix
        val floatBytes = HwChargerProtocol.encodeFloat(230.5f)
        val combined = prefix + floatBytes
        assertEquals(230.5f, HwChargerProtocol.decodeFloat(combined, 2))
    }

    @Test
    fun decodeFloat_insufficientBytes_returnsZero() {
        assertEquals(0f, HwChargerProtocol.decodeFloat(byteArrayOf(1, 2), 0))
    }

    // ── Int encoding ───────────────────────────────────────────────

    @Test
    fun encodeInt_littleEndian() {
        val bytes = HwChargerProtocol.encodeInt(4000) // 0x00000FA0
        assertEquals(0xA0.toByte(), bytes[0]) // LSB first
        assertEquals(0x0F.toByte(), bytes[1])
        assertEquals(0x00.toByte(), bytes[2])
        assertEquals(0x00.toByte(), bytes[3])
    }

    // ── Auth frame ─────────────────────────────────────────────────

    @Test
    fun buildAuthFrame_correctSize() {
        // MD5 hash is 16 bytes → 32 hex chars + null = 33 payload bytes
        // Frame: [size=35, cmd=2, hex(32), null, checksum] = 36 bytes
        val fakeMd5 = ByteArray(16) { it.toByte() }
        val frame = HwChargerProtocol.buildAuthFrame(fakeMd5)
        assertEquals(36, frame.size)
        assertEquals(35, frame[0].toInt() and 0xFF) // size
        assertEquals(2, frame[1].toInt() and 0xFF) // CMD_AUTH
        assertEquals(0, frame[34].toInt() and 0xFF) // null terminator
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    @Test
    fun buildAuthFrame_lowercaseHex() {
        // MD5 of known input, verify hex chars are lowercase
        val hash = byteArrayOf(
            0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x01,
            0x23, 0x45, 0x67, 0x89.toByte(),
            0x00, 0x11, 0x22, 0x33,
            0x44, 0x55, 0x66, 0x77
        )
        val frame = HwChargerProtocol.buildAuthFrame(hash)
        // Extract hex string from frame (bytes 2..33)
        val hexString = String(CharArray(32) { frame[it + 2].toInt().toChar() })
        assertEquals("abcdef01234567890011223344556677", hexString)
    }

    // ── Output toggle ──────────────────────────────────────────────

    @Test
    fun buildOutputToggle_enable_invertedLogic() {
        // Enable → byte is 0 (Huawei inverted)
        val frame = HwChargerProtocol.buildOutputToggle(true)
        assertEquals(0x0C, frame[1].toInt() and 0xFF) // CMD_START_STOP
        assertEquals(0, frame[2].toInt() and 0xFF) // 0 = enable
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    @Test
    fun buildOutputToggle_disable_invertedLogic() {
        val frame = HwChargerProtocol.buildOutputToggle(false)
        assertEquals(0x0C, frame[1].toInt() and 0xFF)
        assertEquals(1, frame[2].toInt() and 0xFF) // 1 = disable
    }

    // ── Float command ──────────────────────────────────────────────

    @Test
    fun buildFloatCommand_voltage_correctFormat() {
        val frame = HwChargerProtocol.buildFloatCommand(HwChargerProtocol.CMD_SET_VOLTAGE, 84.0f)
        assertEquals(7, frame.size) // size(1) + cmd(1) + float(4) + checksum(1)
        assertEquals(6, frame[0].toInt() and 0xFF) // size
        assertEquals(0x07, frame[1].toInt() and 0xFF) // CMD_SET_VOLTAGE
        val decoded = HwChargerProtocol.decodeFloat(frame, 2)
        assertEquals(84.0f, decoded)
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }

    // ── MTU chunking ───────────────────────────────────────────────

    @Test
    fun chunkForMtu_smallFrame_singleChunk() {
        val frame = ByteArray(15)
        val chunks = HwChargerProtocol.chunkForMtu(frame, 20)
        assertEquals(1, chunks.size)
        assertEquals(15, chunks[0].size)
    }

    @Test
    fun chunkForMtu_authFrame_twoChunks() {
        val frame = ByteArray(36) // Auth frame size
        val chunks = HwChargerProtocol.chunkForMtu(frame, 20)
        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(16, chunks[1].size)
    }

    @Test
    fun chunkForMtu_exactMultiple_noEmptyTrailingChunk() {
        val frame = ByteArray(40)
        val chunks = HwChargerProtocol.chunkForMtu(frame, 20)
        assertEquals(2, chunks.size)
        assertEquals(20, chunks[0].size)
        assertEquals(20, chunks[1].size)
    }

    // ── Power limit command ────────────────────────────────────────

    @Test
    fun buildPowerLimitCommand_correctFormat() {
        val frame = HwChargerProtocol.buildPowerLimitCommand(4000)
        assertEquals(7, frame.size) // size(1) + cmd(1) + int(4) + checksum(1)
        assertEquals(0x27, frame[1].toInt() and 0xFF) // CMD_POWER_LIMIT
        assertTrue(HwChargerProtocol.isChecksumValid(frame))
    }
}
