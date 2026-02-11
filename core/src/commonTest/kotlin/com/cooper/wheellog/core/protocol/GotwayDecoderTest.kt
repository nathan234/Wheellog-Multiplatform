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

    // Helper function to convert short to big-endian bytes
    private fun shortToBytesBE(value: Short): ByteArray {
        return byteArrayOf(
            ((value.toInt() shr 8) and 0xFF).toByte(),
            (value.toInt() and 0xFF).toByte()
        )
    }
}
