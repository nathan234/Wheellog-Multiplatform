package org.freewheel.core.charger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HwChargerDecoderTest {

    private val decoder = HwChargerDecoder()
    private val defaultState = ChargerState()

    // ── Helper: build a valid Status frame (cmd 0x06, 49 bytes) ────

    private fun buildStatusFrame(
        acVoltage: Float = 230f,
        acCurrent: Float = 2.5f,
        acFrequency: Float = 50f,
        temp1: Float = 35f,
        temp2: Float = 38f,
        dcVoltage: Float = 84f,
        dcCurrent: Float = 5.0f,
        currentLimit: Float = 15f,
        efficiency: Float = 92f,
        outputEnabled: Boolean = true
    ): ByteArray {
        val frame = ByteArray(49)
        frame[0] = 48 // size = 49 - 1
        frame[1] = 6  // CMD_STATUS

        fun putFloat(offset: Int, value: Float) {
            val bytes = HwChargerProtocol.encodeFloat(value)
            bytes.copyInto(frame, offset)
        }

        putFloat(2, acVoltage)
        putFloat(6, acCurrent)
        putFloat(10, acFrequency)
        putFloat(14, temp1)
        putFloat(18, temp2)
        putFloat(22, dcVoltage)
        putFloat(26, dcCurrent)
        putFloat(30, currentLimit)
        putFloat(34, efficiency)
        frame[38] = if (outputEnabled) 0 else 1 // Huawei inverted

        frame[48] = HwChargerProtocol.checksum(frame)
        return frame
    }

    // ── Helper: build a Setpoints frame (cmd 0x05) ─────────────────

    private fun buildSetpointsFrame(
        targetVoltage: Float = 84.0f,
        targetCurrent: Float = 10.0f
    ): ByteArray {
        val frame = ByteArray(102)
        frame[0] = 101 // size
        frame[1] = 5   // CMD_SETPOINTS

        HwChargerProtocol.encodeFloat(targetVoltage).copyInto(frame, 2)
        HwChargerProtocol.encodeFloat(targetCurrent).copyInto(frame, 6)

        frame[101] = HwChargerProtocol.checksum(frame)
        return frame
    }

    // ── Helper: build a Firmware frame (cmd 0x01) ──────────────────

    private fun buildFirmwareFrame(version: String): ByteArray {
        val versionBytes = version.encodeToByteArray()
        val frame = ByteArray(versionBytes.size + 3) // size + cmd + string + checksum
        frame[0] = (frame.size - 1).toByte() // size
        frame[1] = 1 // CMD_FIRMWARE
        versionBytes.copyInto(frame, 2)
        frame[frame.size - 1] = HwChargerProtocol.checksum(frame)
        return frame
    }

    // ── Helper: build Auth response ────────────────────────────────

    private fun buildAuthResponse(success: Boolean): ByteArray {
        // {3, 2, result, checksum}
        val frame = byteArrayOf(3, 2, if (success) 1 else 0, 0)
        frame[3] = HwChargerProtocol.checksum(frame)
        return frame
    }

    // ── Status frame decoding ──────────────────────────────────────

    @Test
    fun decodeStatus_allFieldsParsed() {
        val frame = buildStatusFrame(
            acVoltage = 231.5f,
            acCurrent = 2.3f,
            acFrequency = 50.1f,
            temp1 = 42.5f,
            temp2 = 39.0f,
            dcVoltage = 84.2f,
            dcCurrent = 5.1f,
            currentLimit = 15.0f,
            efficiency = 91.5f,
            outputEnabled = true
        )
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertEquals(231.5f, result.acVoltage)
        assertEquals(2.3f, result.acCurrent)
        assertEquals(50.1f, result.acFrequency)
        assertEquals(42.5f, result.temperature1)
        assertEquals(39.0f, result.temperature2)
        assertEquals(84.2f, result.dcVoltage)
        assertEquals(5.1f, result.dcCurrent)
        assertEquals(15.0f, result.currentLimitingPoint)
        assertEquals(91.5f, result.efficiency)
        assertTrue(result.isOutputEnabled)
    }

    @Test
    fun decodeStatus_outputDisabled_invertedLogic() {
        val frame = buildStatusFrame(outputEnabled = false)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertFalse(result.isOutputEnabled)
    }

    @Test
    fun decodeStatus_calculatedFields() {
        val frame = buildStatusFrame(dcVoltage = 84f, dcCurrent = 5f, acVoltage = 230f, acCurrent = 2f)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertEquals(420f, result.dcPower, 0.01f) // 84 * 5
        assertEquals(460f, result.acPower, 0.01f) // 230 * 2
        assertTrue(result.isCharging) // dcCurrent > 0.1
    }

    @Test
    fun decodeStatus_notCharging() {
        val frame = buildStatusFrame(dcCurrent = 0.05f)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertFalse(result.isCharging) // 0.05 < 0.1
    }

    @Test
    fun decodeStatus_tooShort_returnsNull() {
        // Frame shorter than 49 bytes but valid checksum
        val frame = byteArrayOf(5, 6, 0, 0, 0, 6) // size=5, cmd=6, 3 bytes, checksum
        frame[5] = HwChargerProtocol.checksum(frame)
        val result = decoder.decode(frame, defaultState)
        assertNull(result)
    }

    // ── Setpoints frame decoding ───────────────────────────────────

    @Test
    fun decodeSetpoints_voltageAndCurrent() {
        val frame = buildSetpointsFrame(targetVoltage = 84.0f, targetCurrent = 10.0f)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertEquals(84.0f, result.targetVoltage)
        assertEquals(10.0f, result.targetCurrent)
    }

    // ── Firmware frame decoding ────────────────────────────────────

    @Test
    fun decodeFirmware_versionString() {
        val frame = buildFirmwareFrame("tft_1.1.5")
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertEquals("tft_1.1.5", result.firmwareVersion)
    }

    // ── Auth response decoding ─────────────────────────────────────

    @Test
    fun decodeAuth_success() {
        val frame = buildAuthResponse(true)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
    }

    @Test
    fun decodeAuth_failure() {
        val frame = buildAuthResponse(false)
        val result = decoder.decode(frame, defaultState)
        assertNotNull(result)
        assertFalse(result.isAuthenticated)
    }

    // ── Frame accumulation ─────────────────────────────────────────

    @Test
    fun decode_splitAcrossMultipleCalls_reassembles() {
        val fullFrame = buildStatusFrame()
        // Split into 3 parts
        val part1 = fullFrame.copyOfRange(0, 15)
        val part2 = fullFrame.copyOfRange(15, 30)
        val part3 = fullFrame.copyOfRange(30, fullFrame.size)

        assertNull(decoder.decode(part1, defaultState))
        assertNull(decoder.decode(part2, defaultState))
        val result = decoder.decode(part3, defaultState)
        assertNotNull(result)
        assertEquals(230f, result.acVoltage) // default from buildStatusFrame
    }

    @Test
    fun decode_twoFramesInOneCall_bothParsed() {
        val statusFrame = buildStatusFrame(dcVoltage = 84f)
        val setpointsFrame = buildSetpointsFrame(targetVoltage = 84.0f)
        val combined = statusFrame + setpointsFrame

        val result = decoder.decode(combined, defaultState)
        assertNotNull(result)
        // Both frames decoded — result is after both
        assertEquals(84f, result.dcVoltage)
        assertEquals(84.0f, result.targetVoltage)
    }

    @Test
    fun decode_invalidChecksum_skipped() {
        val frame = buildStatusFrame()
        frame[48] = (frame[48] + 1).toByte() // Corrupt checksum
        val result = decoder.decode(frame, defaultState)
        assertNull(result)
    }

    @Test
    fun decode_invalidSize_clearsBuffer() {
        // Size byte = 0 (invalid, < 2)
        val badData = byteArrayOf(0, 0, 0, 0)
        assertNull(decoder.decode(badData, defaultState))

        // Now send a valid frame — should still work (buffer was cleared)
        val authFrame = buildAuthResponse(true)
        val result = decoder.decode(authFrame, defaultState)
        assertNotNull(result)
        assertTrue(result.isAuthenticated)
    }

    @Test
    fun decode_sizeOne_clearsBuffer() {
        // Size = 1 is also invalid (< 2)
        val badData = byteArrayOf(1, 0xFF.toByte())
        assertNull(decoder.decode(badData, defaultState))
    }

    @Test
    fun decode_incompleteFrame_waitsForMore() {
        // Send just the size byte + command, no checksum yet
        val partial = byteArrayOf(48, 6)
        assertNull(decoder.decode(partial, defaultState))
    }

    // ── Reset ──────────────────────────────────────────────────────

    @Test
    fun reset_clearsAccumulatedData() {
        // Send partial frame
        val statusFrame = buildStatusFrame()
        val part1 = statusFrame.copyOfRange(0, 20)
        decoder.decode(part1, defaultState)

        // Reset
        decoder.reset()

        // Remainder should NOT complete a frame (buffer was cleared)
        val part2 = statusFrame.copyOfRange(20, statusFrame.size)
        assertNull(decoder.decode(part2, defaultState))
    }

    // ── ACK frames (no state change) ───────────────────────────────

    @Test
    fun decode_ackFrame_returnsNull() {
        // ACK for CMD_SET_VOLTAGE (0x07): {3, 7, 1, checksum}
        val frame = byteArrayOf(3, 7, 1, 0)
        frame[3] = HwChargerProtocol.checksum(frame)
        val result = decoder.decode(frame, defaultState)
        assertNull(result) // ACKs don't produce state updates
    }

    // ── State preservation ─────────────────────────────────────────

    @Test
    fun decode_preservesExistingState() {
        // First decode sets firmware
        val fwFrame = buildFirmwareFrame("v1.0")
        val state1 = decoder.decode(fwFrame, defaultState)
        assertNotNull(state1)

        // Second decode sets status — firmware should be preserved
        val statusFrame = buildStatusFrame(dcVoltage = 50f)
        val state2 = decoder.decode(statusFrame, state1)
        assertNotNull(state2)
        assertEquals("v1.0", state2.firmwareVersion)
        assertEquals(50f, state2.dcVoltage)
    }
}
