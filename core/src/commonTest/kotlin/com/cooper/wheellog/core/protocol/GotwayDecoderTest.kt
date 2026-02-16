package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for GotwayDecoder that verify the KMP decoder produces identical
 * results to the original Android adapter.
 *
 * Test data and expected values are taken directly from the original
 * GotwayAdapterTest.kt to ensure behavioral equivalence.
 */
class GotwayDecoderTest {

    private val decoder = GotwayDecoder()
    private val config = DecoderConfig(
        useMph = false,
        useFahrenheit = false,
        useCustomPercents = false
    )

    // Helper to convert hex string to ByteArray
    private fun String.hexToByteArray(): ByteArray {
        val hex = this.replace(" ", "")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun `decode with corrupted data 1-30 units returns null`() {
        var byteArray = byteArrayOf()
        for (i in 0..29) {
            byteArray += i.toByte()
            val result = decoder.decode(byteArray, WheelState(), config)
            assertNull(result, "Should return null for corrupted data of size ${i + 1}")
        }
    }

    @Test
    fun `decode with normal data`() {
        // Same test data from GotwayAdapterTest
        val voltage: Short = 6000
        val speed: Short = -1111
        val temperature: Short = 99
        val distance: Short = 3231
        val phaseCurrent: Short = -8322

        val header = byteArrayOf(0x55, 0xAA.toByte())
        val byteArray = header +
            shortToBytesBE(voltage) +
            shortToBytesBE(speed) +
            byteArrayOf(0, 0) +
            shortToBytesBE(distance) +
            shortToBytesBE(phaseCurrent) +
            shortToBytesBE(temperature) +
            byteArrayOf(14, 15, 16, 17, 0, 0x18, 0x5A, 0x5A, 0x5A, 0x5A)

        val result = decoder.decode(byteArray, WheelState(), config)

        assertNotNull(result)
        assertTrue(result.hasNewData)

        // Default gotwayNegative=0 takes abs() of speed, matching Android legacy default
        // speed = abs(round(-1111 * 3.6)) = abs(-4000) = 4000
        val expectedSpeed = abs((speed * 3.6).roundToInt())
        assertEquals(expectedSpeed, result.newState.speed)

        // Temperature uses MPU6050 formula: (raw/340 + 36.53) * 100
        // raw = 99, so temp = (99/340 + 36.53) * 100 = 3682
        assertEquals(3682, result.newState.temperature)

        // Phase current is abs() with gotwayNegative=0, matching Android legacy default
        assertEquals(abs(phaseCurrent.toInt()), result.newState.phaseCurrent)

        // Voltage is passed through (will be scaled by scaleVoltage if configured)
        assertEquals(voltage.toInt(), result.newState.voltage)

        // Distance from frame
        assertEquals(distance.toLong(), result.newState.wheelDistance)

        // Battery level: voltage 6000 -> 60V
        // Standard percent: (6000 - 5290) / 13 = 54
        assertEquals(54, result.newState.batteryLevel)
    }

    @Test
    fun `decode with 2020 board data`() {
        // Same test data from GotwayAdapterTest
        val byteArray1 = "55AA19C1000000000000008CF0000001FFF80018".hexToByteArray()
        val byteArray2 = "5A5A5A5A55AA000060D248001C20006400010007".hexToByteArray()
        val byteArray3 = "000804185A5A5A5A".hexToByteArray()

        var state = WheelState()

        val result1 = decoder.decode(byteArray1, state, config)
        if (result1 != null) state = result1.newState

        val result2 = decoder.decode(byteArray2, state, config)
        assertNotNull(result2)
        state = result2.newState

        val result3 = decoder.decode(byteArray3, state, config)
        if (result3 != null) state = result3.newState

        // Verify decode produced data - values verified against original adapter
        // Speed = 0 (wheel stationary)
        assertEquals(0, abs(state.speed))

        // Voltage: 6593 (65.93V) - directly from frame
        assertEquals(6593, state.voltage)

        // Total distance: 24786m
        assertEquals(24786L, state.totalDistance)

        // Battery level: 100% (full charge)
        assertEquals(100, state.batteryLevel)
    }

    @Test
    fun `decode with new board data`() {
        // Same test data from GotwayAdapterTest - multi-frame packet sequence
        val byteArray1 = "55aa17750538007602eefb64f494148100090018".hexToByteArray()
        val byteArray2 = "5a5a5a5a55aa0032000004b10000000013880000".hexToByteArray()
        val byteArray3 = "000001005a5a5a5a55aa00000000000000000000".hexToByteArray()
        val byteArray4 = "00000000000003005a5a5a5a55aa003c278c4900".hexToByteArray()
        val byteArray5 = "1c2000c800000000001204185a5a5a5a55aa022c".hexToByteArray()
        val byteArray6 = "000000000000000000000000000007185a5a5a5a".hexToByteArray()

        var state = WheelState()

        // Process two full rotations of the packet sequence
        // (wheel sends packets in sequence, need multiple to get all data)
        for (pass in 1..2) {
            for (data in listOf(byteArray1, byteArray2, byteArray3, byteArray4, byteArray5, byteArray6)) {
                val result = decoder.decode(data, state, config)
                if (result != null) state = result.newState
            }
        }

        // Verify key values from decoded data
        // Speed: non-zero (wheel is moving)
        assertTrue(abs(state.speed) > 0, "Speed should be non-zero")

        // Voltage: ~120V battery
        assertTrue(state.voltage > 11000 && state.voltage < 13000, "Voltage should be ~120V")

        // Total distance should be populated
        assertTrue(state.totalDistance > 0, "Total distance should be set")

        // Battery should be reasonable percentage
        assertTrue(state.batteryLevel in 0..100, "Battery should be 0-100%")
    }

    @Test
    fun `decode veteran old board data`() {
        val veteranDecoder = VeteranDecoder()
        val byteArray1 = "DC5A5C2025D600003BF500003BF50000FFDE1399".hexToByteArray()
        val byteArray2 = "0DEF0000024602460000000000000000".hexToByteArray()

        var state = WheelState()

        val result1 = veteranDecoder.decode(byteArray1, state, config)
        if (result1 != null) state = result1.newState

        val result2 = veteranDecoder.decode(byteArray2, state, config)
        assertNotNull(result2)
        state = result2.newState

        // Original expected values from VeteranAdapterTest
        assertEquals(0, abs(state.speed / 100))
        assertEquals(50, state.temperature / 100)
        assertEquals(9686, state.voltage) // 96.86V
        assertEquals(-340, state.phaseCurrent) // -3.4A * 100 -> stored as * 10
        assertEquals(15349L, state.wheelDistance)
        assertEquals(15349L, state.totalDistance)
        assertEquals(90, state.batteryLevel)
    }

    @Test
    fun `decode kingsong live data`() {
        val ksDecoder = KingsongDecoder()

        // Real data from KingsongAdapterTest
        val byteArray = "aa554b532d5331382d30323035000000bb1484fd".hexToByteArray() +
            "aa556919030200009f36d700140500e0a9145a5a".hexToByteArray()

        var state = WheelState()

        // First packet - model name
        val result1 = ksDecoder.decode(
            "aa554b532d5331382d30323035000000bb1484fd".hexToByteArray(),
            state,
            config
        )
        if (result1 != null) state = result1.newState

        // Second packet - live data
        val result2 = ksDecoder.decode(
            "aa556919030200009f36d700140500e0a9145a5a".hexToByteArray(),
            state,
            config
        )
        assertNotNull(result2)
        state = result2.newState

        // Original expected values from KingsongAdapterTest
        assertEquals("KS-S18", state.model)
        assertEquals(515, state.speed) // 5.15 * 100
        assertEquals(13, state.temperature / 100)
        assertEquals(6505, state.voltage) // 65.05V
        assertEquals(215, state.current) // 2.15A * 100
        assertEquals(13983L, state.totalDistance)
    }

    // ==================== Voltage Scaling ====================

    @Test
    fun `voltage scaling default (67V) is 1x`() {
        val cfg = config.copy(gotwayVoltage = 0)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 1.0 = 6000
        assertEquals(6000, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 84V is 1_25x`() {
        val cfg = config.copy(gotwayVoltage = 1)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 1.25 = 7500
        assertEquals(7500, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 100V is 1_5x`() {
        val cfg = config.copy(gotwayVoltage = 2)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 1.5 = 9000
        assertEquals(9000, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 126V is 1_738x`() {
        val cfg = config.copy(gotwayVoltage = 3)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 1.7380952... = 10428.57... rounds to 10429
        assertEquals(10429, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 134V is 2x`() {
        val cfg = config.copy(gotwayVoltage = 4)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 2.0 = 12000
        assertEquals(12000, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 168V is 2_5x`() {
        val cfg = config.copy(gotwayVoltage = 5)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 2.5 = 15000
        assertEquals(15000, result.newState.voltage)
    }

    @Test
    fun `voltage scaling 151V is 2_25x`() {
        val cfg = config.copy(gotwayVoltage = 6)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        // 6000 * 2.25 = 13500
        assertEquals(13500, result.newState.voltage)
    }

    @Test
    fun `voltage scaling unknown value falls back to 1x`() {
        val cfg = config.copy(gotwayVoltage = 99)
        val result = decodeNormalData(voltage = 6000, config = cfg)
        assertNotNull(result)
        assertEquals(6000, result.newState.voltage)
    }

    // ==================== Veteran Model Names ====================

    @Test
    fun `veteran decoder identifies Sherman (mVer 1)`() {
        val state = decodeVeteranFrame(mVer = 1)
        assertEquals("Sherman", state.model)
    }

    @Test
    fun `veteran decoder identifies Abrams (mVer 2)`() {
        val state = decodeVeteranFrame(mVer = 2)
        assertEquals("Abrams", state.model)
    }

    @Test
    fun `veteran decoder identifies Sherman S (mVer 3)`() {
        val state = decodeVeteranFrame(mVer = 3)
        assertEquals("Sherman S", state.model)
    }

    @Test
    fun `veteran decoder identifies Patton (mVer 4)`() {
        val state = decodeVeteranFrame(mVer = 4)
        assertEquals("Patton", state.model)
    }

    @Test
    fun `veteran decoder identifies Lynx (mVer 5)`() {
        val state = decodeVeteranFrame(mVer = 5)
        assertEquals("Lynx", state.model)
    }

    @Test
    fun `veteran decoder identifies Sherman L (mVer 6)`() {
        val state = decodeVeteranFrame(mVer = 6)
        assertEquals("Sherman L", state.model)
    }

    @Test
    fun `veteran decoder identifies Patton S (mVer 7)`() {
        val state = decodeVeteranFrame(mVer = 7)
        assertEquals("Patton S", state.model)
    }

    @Test
    fun `veteran decoder identifies Oryx (mVer 8)`() {
        val state = decodeVeteranFrame(mVer = 8)
        assertEquals("Oryx", state.model)
    }

    @Test
    fun `veteran decoder identifies Nosfet Apex (mVer 42)`() {
        val state = decodeVeteranFrame(mVer = 42)
        assertEquals("Nosfet Apex", state.model)
    }

    @Test
    fun `veteran decoder identifies Nosfet Aero (mVer 43)`() {
        val state = decodeVeteranFrame(mVer = 43)
        assertEquals("Nosfet Aero", state.model)
    }

    // ==================== Kingsong Model Detection ====================

    @Test
    fun `kingsong identifies S16 as 84V wheel`() {
        val ksDecoder = KingsongDecoder()
        // Send name frame "KS-S16-0001"
        val namePacket = buildKsNamePacket("KS-S16-0001")
        val livePacket = buildKsLivePacket(voltage = 8000)
        var state = WheelState()

        val r1 = ksDecoder.decode(namePacket, state, config)
        if (r1 != null) state = r1.newState
        assertEquals("KS-S16", state.model)

        val r2 = ksDecoder.decode(livePacket, state, config)
        assertNotNull(r2)
        // 84V wheel: 8000 -> (8000-6250)/20 = 87%
        assertEquals(87, r2.newState.batteryLevel)
    }

    @Test
    fun `kingsong identifies F18P as 151V wheel`() {
        val ksDecoder = KingsongDecoder()
        val namePacket = buildKsNamePacket("KS-F18P-001")
        val livePacket = buildKsLivePacket(voltage = 14000)
        var state = WheelState()

        val r1 = ksDecoder.decode(namePacket, state, config)
        if (r1 != null) state = r1.newState
        assertEquals("KS-F18P", state.model)

        val r2 = ksDecoder.decode(livePacket, state, config)
        assertNotNull(r2)
        // 151V wheel: (14000-11250)/36 = 76%
        assertEquals(76, r2.newState.batteryLevel)
    }

    @Test
    fun `kingsong identifies F22P as 176V wheel`() {
        val ksDecoder = KingsongDecoder()
        val namePacket = buildKsNamePacket("KS-F22P-001")
        val livePacket = buildKsLivePacket(voltage = 16000)
        var state = WheelState()

        val r1 = ksDecoder.decode(namePacket, state, config)
        if (r1 != null) state = r1.newState
        assertEquals("KS-F22P", state.model)

        val r2 = ksDecoder.decode(livePacket, state, config)
        assertNotNull(r2)
        // 176V wheel: (16000-13125)/42 = 68%
        assertEquals(68, r2.newState.batteryLevel)
    }

    @Test
    fun `kingsong identifies S19 as 100V wheel`() {
        val ksDecoder = KingsongDecoder()
        val namePacket = buildKsNamePacket("KS-S19-0001")
        val livePacket = buildKsLivePacket(voltage = 9000)
        var state = WheelState()

        val r1 = ksDecoder.decode(namePacket, state, config)
        if (r1 != null) state = r1.newState
        assertEquals("KS-S19", state.model)

        val r2 = ksDecoder.decode(livePacket, state, config)
        assertNotNull(r2)
        // 100V wheel: (9000-7500)/24 = 62%
        assertEquals(62, r2.newState.batteryLevel)
    }

    // ==================== InMotionV2 Model IDs ====================

    @Test
    fun `inmotionV2 all model IDs resolve correctly`() {
        val expected = mapOf(
            Pair(6, 1) to "InMotion V11",
            Pair(6, 2) to "InMotion V11y",
            Pair(7, 1) to "InMotion V12 HS",
            Pair(7, 2) to "InMotion V12 HT",
            Pair(7, 3) to "InMotion V12 PRO",
            Pair(8, 1) to "InMotion V13",
            Pair(8, 2) to "InMotion V13 PRO",
            Pair(9, 1) to "InMotion V14 50GB",
            Pair(9, 2) to "InMotion V14 50S",
            Pair(11, 1) to "InMotion V12S",
            Pair(12, 1) to "InMotion V9"
        )

        for ((ids, name) in expected) {
            val model = InMotionV2Decoder.Model.findById(ids.first, ids.second)
            assertEquals(name, model.displayName, "Model for series=${ids.first}, type=${ids.second}")
        }
    }

    @Test
    fun `inmotionV2 cell counts are correct`() {
        assertEquals(20, InMotionV2Decoder.Model.V11.cellCount)
        assertEquals(20, InMotionV2Decoder.Model.V11Y.cellCount)
        assertEquals(24, InMotionV2Decoder.Model.V12HS.cellCount)
        assertEquals(24, InMotionV2Decoder.Model.V12HT.cellCount)
        assertEquals(24, InMotionV2Decoder.Model.V12PRO.cellCount)
        assertEquals(30, InMotionV2Decoder.Model.V13.cellCount)
        assertEquals(30, InMotionV2Decoder.Model.V13PRO.cellCount)
        assertEquals(32, InMotionV2Decoder.Model.V14g.cellCount)
        assertEquals(32, InMotionV2Decoder.Model.V14s.cellCount)
        assertEquals(20, InMotionV2Decoder.Model.V12S.cellCount)
        assertEquals(20, InMotionV2Decoder.Model.V9.cellCount)
    }

    @Test
    fun `inmotionV2 unknown series returns UNKNOWN`() {
        val model = InMotionV2Decoder.Model.findById(99, 1)
        assertEquals(InMotionV2Decoder.Model.UNKNOWN, model)
    }

    // ==================== Helpers ====================

    private fun decodeNormalData(voltage: Short = 6000, config: DecoderConfig = this.config): DecodedData? {
        val freshDecoder = GotwayDecoder()
        val header = byteArrayOf(0x55, 0xAA.toByte())
        val byteArray = header +
            shortToBytesBE(voltage) +
            shortToBytesBE(0) + // speed
            byteArrayOf(0, 0) +
            shortToBytesBE(0) + // distance
            shortToBytesBE(0) + // phaseCurrent
            shortToBytesBE(99) + // temperature
            byteArrayOf(14, 15, 16, 17, 0, 0x18, 0x5A, 0x5A, 0x5A, 0x5A)
        return freshDecoder.decode(byteArray, WheelState(), config)
    }

    /**
     * Build a minimal Veteran frame with the given mVer encoded in the version field.
     * Version field = mVer * 1000 (so mVer=5 -> version=5000 -> "5.0.00")
     */
    private fun decodeVeteranFrame(mVer: Int): WheelState {
        val vetDecoder = VeteranDecoder()
        val ver = mVer * 1000
        // Build a 36-byte Veteran frame: header(3) + len(1) + data(32)
        val frame = ByteArray(36)
        // Header: DC 5A 5C
        frame[0] = 0xDC.toByte()
        frame[1] = 0x5A
        frame[2] = 0x5C
        frame[3] = 32 // length
        // Voltage at offset 4 (2 bytes BE): 9686 (96.86V)
        frame[4] = 0x25; frame[5] = 0xD6.toByte()
        // Version at offset 28 (2 bytes BE)
        frame[28] = ((ver shr 8) and 0xFF).toByte()
        frame[29] = (ver and 0xFF).toByte()

        var state = WheelState()
        val result = vetDecoder.decode(frame, state, config)
        assertNotNull(result, "Veteran frame should decode for mVer=$mVer")
        return result.newState
    }

    /**
     * Build a KingSong 0xBB name frame.
     */
    private fun buildKsNamePacket(name: String): ByteArray {
        val packet = ByteArray(20)
        packet[0] = 0xAA.toByte()
        packet[1] = 0x55
        val nameBytes = name.encodeToByteArray()
        for (i in nameBytes.indices) {
            if (i + 2 < 16) packet[i + 2] = nameBytes[i]
        }
        packet[16] = 0xBB.toByte()
        packet[17] = 0x14
        packet[18] = 0x5A
        packet[19] = 0x5A
        return packet
    }

    /**
     * Build a KingSong 0xA9 live data frame with given voltage.
     */
    private fun buildKsLivePacket(voltage: Int): ByteArray {
        val packet = ByteArray(20)
        packet[0] = 0xAA.toByte()
        packet[1] = 0x55
        // Voltage at offset 2-3 (LE)
        packet[2] = (voltage and 0xFF).toByte()
        packet[3] = ((voltage shr 8) and 0xFF).toByte()
        // Mode indicator
        packet[15] = 0xE0.toByte()
        // Frame type
        packet[16] = 0xA9.toByte()
        packet[17] = 0x14
        packet[18] = 0x5A
        packet[19] = 0x5A
        return packet
    }

    // Helper function to convert short to big-endian bytes
    private fun shortToBytesBE(value: Short): ByteArray {
        return byteArrayOf(
            ((value.toInt() shr 8) and 0xFF).toByte(),
            (value.toInt() and 0xFF).toByte()
        )
    }

    private fun shortToBytesBE(value: Int): ByteArray = shortToBytesBE(value.toShort())
}
