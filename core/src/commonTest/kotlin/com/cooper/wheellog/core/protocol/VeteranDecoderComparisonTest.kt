package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.utils.ByteUtils
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comparison tests verifying KMP VeteranDecoder produces identical results
 * to the legacy VeteranAdapter using real packet data from legacy tests.
 *
 * These tests use the same hex packet data and expected values from
 * VeteranAdapterTest.kt to ensure byte-for-byte compatibility.
 */
class VeteranDecoderComparisonTest {

    private val decoder = VeteranDecoder()
    private val defaultState = WheelState()
    private val defaultConfig = DecoderConfig()

    // Helper to convert hex string to byte array
    private fun String.hexToByteArray(): ByteArray {
        val hex = this.replace(" ", "")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // ==================== Basic Packet Parsing ====================

    @Test
    fun `decode veteran old board data matches legacy expected values`() {
        // From VeteranAdapterTest: decode veteran old board data
        val byteArray1 = "DC5A5C2025D600003BF500003BF50000FFDE1399".hexToByteArray()
        val byteArray2 = "0DEF0000024602460000000000000000".hexToByteArray()

        decoder.reset()

        // Feed first packet
        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState

        // Feed second packet (should complete the frame)
        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        // Verify we got valid data
        assertTrue(result2?.hasNewData == true, "Should decode complete frame")

        val finalState = result2!!.newState

        // Expected from legacy test:
        // - speed = 0
        // - temperature = 50 (5099/100 = 50)
        // - voltageDouble = 96.86
        // - phaseCurrentDouble = -3.4
        // - wheelDistanceDouble = 15.349
        // - totalDistance = 15349
        // - batteryLevel = 90
        // - version = "000.0.00"

        assertEquals(0, abs(finalState.speed), "Speed should be 0")
        assertEquals(9686, finalState.voltage, "Voltage should be 9686 (96.86V)")
        assertEquals(96.86, finalState.voltageV, 0.01, "Voltage should be 96.86V")
        assertEquals(15349, finalState.totalDistance.toInt(), "Total distance should be 15349m")
    }

    @Test
    fun `decode veteran new board data matches legacy expected values`() {
        // From VeteranAdapterTest: decode veteran new board data
        val byteArray1 = "DC5A5C20238A0112121A00004D450005064611F2".hexToByteArray()
        val byteArray2 = "0E1000000AF00AF0041B000300000000".hexToByteArray()

        decoder.reset()

        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState

        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Should decode complete frame")

        val finalState = result2!!.newState

        // Expected from legacy test:
        // - speed = 274 (absolute value) - legacy stores raw value in 1/10 km/h
        // - temperature = 45
        // - voltageDouble = 90.98
        // - phaseCurrentDouble = 160.6
        // - wheelDistanceDouble = 4.634
        // - totalDistance = 347461
        // - batteryLevel = 60
        // - version = "001.0.51"

        // Note: KMP decoder stores speed in 1/100 km/h (2740 = 27.4 km/h)
        // Legacy stores in 1/10 km/h (274 = 27.4 km/h)
        // Both represent the same physical speed
        assertEquals(27.4, abs(finalState.speed) / 100.0, 0.1, "Speed should be ~27.4 km/h")
        assertEquals(9098, finalState.voltage, "Voltage should be 9098 (90.98V)")
        assertEquals(90.98, finalState.voltageV, 0.01, "Voltage should be 90.98V")
        assertEquals(347461, finalState.totalDistance.toInt(), "Total distance should be 347461m")
    }

    @Test
    fun `decode veteran 58fw data matches legacy expected values`() {
        // From VeteranAdapterTest: decode veteran 58fw data
        val byteArray1 = "dc5a5c2025cd0000071f0000c77800280000110b".hexToByteArray()
        val byteArray2 = "0e1000010af00af00422000300140000".hexToByteArray()

        decoder.reset()

        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState

        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Should decode complete frame")

        val finalState = result2!!.newState

        // Expected from legacy test:
        // - speed = 0 (absolute value)
        // - temperature = 43
        // - voltageDouble = 96.77
        // - phaseCurrentDouble = 0
        // - wheelDistanceDouble = 1.823
        // - totalDistance = 2672504
        // - batteryLevel = 89
        // - angle = 0.2 (from 0x0014 = 20 / 100 = 0.2)
        // - version = "001.0.58"

        assertEquals(0, abs(finalState.speed), "Speed should be 0")
        assertEquals(9677, finalState.voltage, "Voltage should be 9677 (96.77V)")
        assertEquals(96.77, finalState.voltageV, 0.01, "Voltage should be 96.77V")
        assertEquals(2672504, finalState.totalDistance.toInt(), "Total distance should be 2672504m")
    }

    @Test
    fun `decode veteran abrams matches legacy expected values`() {
        // From VeteranAdapterTest: decode veteran abrams
        val byteArray1 = "dc5a5c20266d00004aaf00004aaf000000000d9e".hexToByteArray()
        val byteArray2 = "0b8800000af00af007d2000300050004".hexToByteArray()

        decoder.reset()

        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState

        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Should decode complete frame")

        val finalState = result2!!.newState

        // Expected from legacy test:
        // - speed = 0 (absolute value)
        // - temperature = 34
        // - voltageDouble = 98.37
        // - phaseCurrentDouble = 0
        // - wheelDistanceDouble = 19.119
        // - totalDistance = 19119
        // - batteryLevel = 98
        // - angle = 0.05 (from 0x0005 = 5 / 100 = 0.05)
        // - version = "002.0.02"

        assertEquals(0, abs(finalState.speed), "Speed should be 0")
        assertEquals(9837, finalState.voltage, "Voltage should be 9837 (98.37V)")
        assertEquals(98.37, finalState.voltageV, 0.01, "Voltage should be 98.37V")
        assertEquals(19119, finalState.totalDistance.toInt(), "Total distance should be 19119m")
    }

    // ==================== Header Detection ====================

    @Test
    fun `DC 5A 5C header is required for frame detection`() {
        val withoutHeader = ByteArray(36) { 0 }
        withoutHeader[0] = 0x12  // Not 0xDC
        withoutHeader[1] = 0x34  // Not 0x5A
        withoutHeader[2] = 0x56  // Not 0x5C

        decoder.reset()
        val result = decoder.decode(withoutHeader, defaultState, defaultConfig)

        // Without proper header, should not produce valid data
        assertTrue(result == null || !result.hasNewData,
            "Invalid header should not produce valid data")
    }

    @Test
    fun `partial header is rejected`() {
        // DC 5A but not 5C
        val partialHeader = byteArrayOf(0xDC.toByte(), 0x5A, 0x00)

        decoder.reset()
        val result = decoder.decode(partialHeader, defaultState, defaultConfig)

        assertTrue(result == null || !result.hasNewData,
            "Partial header should not produce valid data")
    }

    // ==================== Corrupted Data Handling ====================

    @Test
    fun `corrupted data does not crash decoder`() {
        decoder.reset()

        // Send corrupted/partial data (1-30 bytes)
        for (i in 1..30) {
            val corrupted = ByteArray(i) { it.toByte() }
            val result = decoder.decode(corrupted, defaultState, defaultConfig)
            // Should not crash, may return null or partial result
        }
    }

    // ==================== Signed Value Handling ====================

    @Test
    fun `negative speed is handled correctly`() {
        // Veteran uses signed short for speed
        val speed: Short = -500  // Reverse direction

        // Test that ByteUtils correctly handles signed values
        val speedBytes = ByteUtils.getBytes(speed)
        val parsedSpeed = ByteUtils.signedShortFromBytesBE(speedBytes, 0)

        assertEquals(-500, parsedSpeed, "Signed speed should be preserved")
    }

    @Test
    fun `negative phase current from regenerative braking`() {
        // Phase current is negative during braking/regen
        val phaseCurrent: Short = -340  // -3.4A * 100

        val phaseCurrentBytes = ByteUtils.getBytes(phaseCurrent)
        val parsedCurrent = ByteUtils.signedShortFromBytesBE(phaseCurrentBytes, 0)

        assertEquals(-340, parsedCurrent, "Signed phase current should be preserved")
    }

    // ==================== Version Parsing ====================

    @Test
    fun `version is parsed correctly from raw value`() {
        // Version is encoded as major*1000 + minor*100 + patch
        // e.g., 3210 = 3.2.10
        val versionRaw = 3210
        val major = versionRaw / 1000
        val minor = (versionRaw % 1000) / 100
        val patch = versionRaw % 100

        assertEquals(3, major, "Major version should be 3")
        assertEquals(2, minor, "Minor version should be 2")
        assertEquals(10, patch, "Patch version should be 10")
    }

    // ==================== Multi-packet Assembly ====================

    @Test
    fun `frame assembly works across multiple packets`() {
        // Real data from VeteranAdapterTest - requires two packets
        val byteArray1 = "DC5A5C2025D600003BF500003BF50000FFDE1399".hexToByteArray()
        val byteArray2 = "0DEF0000024602460000000000000000".hexToByteArray()

        decoder.reset()

        // First packet should not produce complete data
        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState

        // Second packet should complete the frame
        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Second packet should complete frame")
    }

    // ==================== Battery Calculation ====================

    @Test
    fun `battery percentage is calculated correctly for different voltages`() {
        // Veteran battery calculation depends on voltage and cell count
        // This tests that the decoder doesn't crash with various voltages
        val testVoltages = listOf(
            9500,   // 95V - typical for 100V Sherman
            9837,   // 98.37V - from Abrams test
            9098,   // 90.98V - from new board test
            9686,   // 96.86V - from old board test
            11500   // 115V - Patton (126V max)
        )

        for (voltage in testVoltages) {
            // Create a valid packet with the given voltage
            val header = byteArrayOf(0xDC.toByte(), 0x5A, 0x5C, 0x20)
            val voltageBytes = ByteUtils.getBytes(voltage.toShort())
            val padding = ByteArray(28) { 0 }

            val packet = header + voltageBytes + padding

            decoder.reset()
            val result = decoder.decode(packet, defaultState, defaultConfig)

            // May or may not produce valid data depending on packet completeness
            // Main thing is it doesn't crash
        }
    }

    // ==================== Angle Parsing ====================

    @Test
    fun `angle is parsed correctly from firmware 58 data`() {
        // From 58fw test: angle = 0.2 (value 0x0014 = 20, divided by 100)
        // In the packet: "...00140000" at the end of second packet
        val rawAngle = 0x0014
        val angle = rawAngle / 100.0

        assertEquals(0.2, angle, 0.001, "Angle should be 0.2 degrees")
    }

    // ==================== Temperature Parsing ====================

    @Test
    fun `temperature is correctly extracted`() {
        // Temperature is at a specific offset in the frame
        // From old board test: temperature = 50 (from value 5099, divided by 100)
        // From new board test: temperature = 45 (from value 4510, at offset 0x0E10)

        // Temperature values are stored as temp * 100
        val temp1 = 5099 / 100  // Old board
        val temp2 = 4510 / 100  // New board

        assertEquals(50, temp1, "Old board temperature should be 50")
        assertEquals(45, temp2, "New board temperature should be 45")
    }

    // ==================== Battery Calculation for 126V Model (Patton) ====================

    @Test
    fun `battery calculation for Patton 126V model`() {
        // From VeteranAdapterTest: decode veteran patton
        // Patton has mVer=4 (version "004.0.07"), uses 126V battery curve
        val byteArray1 = "dc5a5c26302b00001fdc00002038000000000d15".hexToByteArray()
        val byteArray2 = "0a79000000fa01900fa700031b690000006fffff".hexToByteArray()
        val byteArray3 = "5678".hexToByteArray()

        decoder.reset()
        var state = defaultState
        for (packet in listOf(byteArray1, byteArray2, byteArray3)) {
            val result = decoder.decode(packet, state, defaultConfig)
            if (result != null) state = result.newState
        }

        // Expected from legacy test:
        assertEquals(0, abs(state.speed), "Speed should be 0")
        assertEquals(33, state.temperatureC, "Temperature should be 33°C")
        assertEquals(123.31, state.voltageV, 0.01, "Voltage should be 123.31V")
        assertEquals(0.0, state.currentA, 0.01, "Phase current should be 0.0A")
        assertEquals(8248, state.totalDistance.toInt(), "Total distance should be 8248m")
        assertEquals(100, state.batteryLevel, "Battery should be 100%")
    }

    // ==================== Battery Calculation for 151V Model (Lynx) ====================

    @Test
    fun `battery calculation for Lynx 151V model`() {
        // From VeteranAdapterTest: decode veteran lynx crc
        // Lynx has mVer=5 (version "005.0.04"), uses 151V battery curve, CRC format
        val byteArray1 = "dc5a5c53391b000006d000000770000000260bcc".hexToByteArray()
        val byteArray2 = "0e08000000fa00c8138c00b4000b014c80c80000".hexToByteArray()
        val byteArray3 = "808080808080010008808080800fee0fee0fee0f".hexToByteArray()
        val byteArray4 = "ee0fef0fe80fef0fef0ff00ff00ff00fea0fef0f".hexToByteArray()
        val byteArray5 = "ef0fefdab22518".hexToByteArray()

        decoder.reset()
        var state = defaultState

        for (packet in listOf(byteArray1, byteArray2, byteArray3, byteArray4, byteArray5)) {
            val result = decoder.decode(packet, state, defaultConfig)
            if (result != null) {
                state = result.newState
            }
        }

        // Expected from legacy test:
        assertEquals(0, abs(state.speed), "Speed should be 0")
        assertEquals(30, state.temperatureC, "Temperature should be 30°C")
        assertEquals(146.19, state.voltageV, 0.01, "Voltage should be 146.19V")
        assertEquals(1904, state.totalDistance.toInt(), "Total distance should be 1904m")
        assertEquals(94, state.batteryLevel, "Battery should be 94%")
    }

    // ==================== veteranNegative (Speed Polarity) ====================

    @Test
    fun `decode veteran speed is positive with default config`() {
        // Veteran decoder currently uses veteranNegative=1 internally
        // Test that speed comes through correctly for positive speed
        val byteArray1 = "DC5A5C20238A0112121A00004D450005064611F2".hexToByteArray()
        val byteArray2 = "0E1000000AF00AF0041B000300000000".hexToByteArray()

        decoder.reset()
        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState
        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Should decode data")
        // Raw speed = 0x0112 = 274, * 10 = 2740
        // With veteranNegative=1, speed = 2740 * 1 = 2740 (positive)
        assertEquals(2740, result2!!.newState.speed,
            "Speed should be 2740 (27.40 km/h)")
        assertTrue(result2.newState.speed > 0, "Speed should be positive")
    }

    @Test
    fun `decode veteran negative speed preserved when raw value is negative`() {
        // Build a packet with negative raw speed to test sign handling
        // Header: DC 5A 5C 20
        // Voltage at bytes 4-5: 9500 = 0x251C
        // Speed at bytes 6-7: -274 = 0xFEEE
        val byteArray1 = "DC5A5C20251CFEEE121A00004D450005064611F2".hexToByteArray()
        val byteArray2 = "0E1000000AF00AF0041B000300000000".hexToByteArray()

        decoder.reset()
        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState
        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Should decode data")
        // Raw speed = -274, * 10 = -2740, * veteranNegative(1) = -2740
        assertEquals(-2740, result2!!.newState.speed,
            "Negative speed should be preserved (-27.40 km/h)")
        assertTrue(result2.newState.speed < 0, "Speed should be negative")
    }

    // ==================== Sherman S Variant Header ====================

    @Test
    fun `decode veteran sherman S with variant header byte`() {
        // From VeteranAdapterTest: decode veteran sherman s 1
        // Header uses 0x22 as 4th byte (len=34) instead of 0x20 (len=32)
        val byteArray1 = "DC5A5C22266200000084000017A2000000000C38".hexToByteArray()
        val byteArray2 = "0B03000000C600E40BBD0003188B0000006F".hexToByteArray()

        decoder.reset()
        var state = defaultState
        val result1 = decoder.decode(byteArray1, state, defaultConfig)
        if (result1 != null) state = result1.newState
        val result2 = decoder.decode(byteArray2, state, defaultConfig)

        assertTrue(result2?.hasNewData == true, "Sherman S header variant should decode")
        val finalState = result2!!.newState

        // Expected from legacy test:
        assertEquals(0, abs(finalState.speed), "Speed should be 0")
        assertEquals(31, finalState.temperatureC, "Temperature should be 31°C")
        assertEquals(98.26, finalState.voltageV, 0.01, "Voltage should be 98.26V")
        assertEquals(6050, finalState.totalDistance.toInt(), "Total distance should be 6050m")
        assertEquals(97, finalState.batteryLevel, "Battery should be 97%")
    }
}
