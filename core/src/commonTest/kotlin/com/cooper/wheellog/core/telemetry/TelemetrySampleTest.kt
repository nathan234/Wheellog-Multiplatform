package com.cooper.wheellog.core.telemetry

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetrySampleTest {

    @Test
    fun `fromWheelState maps speed correctly`() {
        val state = WheelState(speed = 2500) // 25.00 km/h
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(25.0, sample.speedKmh)
    }

    @Test
    fun `fromWheelState maps voltage correctly`() {
        val state = WheelState(voltage = 8400) // 84.00 V
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(84.0, sample.voltageV)
    }

    @Test
    fun `fromWheelState maps current correctly`() {
        val state = WheelState(current = 1500) // 15.00 A
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(15.0, sample.currentA)
    }

    @Test
    fun `fromWheelState maps power correctly`() {
        val state = WheelState(power = 150000) // 1500.00 W
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(1500.0, sample.powerW)
    }

    @Test
    fun `fromWheelState maps temperature correctly`() {
        val state = WheelState(temperature = 3500) // 35°C
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(35.0, sample.temperatureC)
    }

    @Test
    fun `fromWheelState maps battery correctly`() {
        val state = WheelState(batteryLevel = 80)
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(80.0, sample.batteryPercent)
    }

    @Test
    fun `fromWheelState maps pwm correctly`() {
        val state = WheelState(calculatedPwm = 0.45) // 45%
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(45.0, sample.pwmPercent)
    }

    @Test
    fun `fromWheelState passes timestamp and gpsSpeed`() {
        val state = WheelState()
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 42L, gpsSpeedKmh = 12.5)
        assertEquals(42L, sample.timestampMs)
        assertEquals(12.5, sample.gpsSpeedKmh)
    }

    @Test
    fun `fromWheelState defaults gpsSpeed to zero`() {
        val state = WheelState()
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 1000L)
        assertEquals(0.0, sample.gpsSpeedKmh)
    }

    @Test
    fun `fromWheelState maps all fields from realistic state`() {
        val state = WheelState(
            speed = 3000,         // 30.0 km/h
            voltage = 6720,       // 67.20 V
            current = 850,        // 8.50 A
            power = 57120,        // 571.20 W
            temperature = 4200,   // 42°C
            batteryLevel = 65,
            calculatedPwm = 0.32, // 32%
            wheelType = WheelType.KINGSONG,
            model = "KS-S18"
        )
        val sample = TelemetrySample.fromWheelState(state, timestampMs = 99999L, gpsSpeedKmh = 28.5)

        assertEquals(99999L, sample.timestampMs)
        assertEquals(30.0, sample.speedKmh)
        assertEquals(67.2, sample.voltageV)
        assertEquals(8.5, sample.currentA)
        assertEquals(571.2, sample.powerW)
        assertEquals(42.0, sample.temperatureC)
        assertEquals(65.0, sample.batteryPercent)
        assertEquals(32.0, sample.pwmPercent)
        assertEquals(28.5, sample.gpsSpeedKmh)
    }
}
