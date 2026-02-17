package com.cooper.wheellog.core.util

import com.cooper.wheellog.core.domain.AlarmType
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.utils.DisplayUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayUtilsTest {

    // ==================== Speed Formatting ====================

    @Test
    fun `formatSpeed metric returns km per h`() {
        assertEquals("25 km/h", DisplayUtils.formatSpeed(25.0, useMph = false))
    }

    @Test
    fun `formatSpeed imperial converts to mph`() {
        assertEquals("16 mph", DisplayUtils.formatSpeed(25.0, useMph = true))
    }

    @Test
    fun `formatSpeed with decimals`() {
        assertEquals("25.5 km/h", DisplayUtils.formatSpeed(25.5, useMph = false, decimals = 1))
    }

    @Test
    fun `speedUnit returns correct unit`() {
        assertEquals("mph", DisplayUtils.speedUnit(true))
        assertEquals("km/h", DisplayUtils.speedUnit(false))
    }

    // ==================== Distance Formatting ====================

    @Test
    fun `formatDistance metric returns km`() {
        assertEquals("1.50 km", DisplayUtils.formatDistance(1.5, useMph = false))
    }

    @Test
    fun `formatDistance imperial converts to miles`() {
        assertEquals("0.93 mi", DisplayUtils.formatDistance(1.5, useMph = true))
    }

    @Test
    fun `formatDistance with 1 decimal`() {
        assertEquals("100.0 km", DisplayUtils.formatDistance(100.0, useMph = false, decimals = 1))
    }

    @Test
    fun `distanceUnit returns correct unit`() {
        assertEquals("mi", DisplayUtils.distanceUnit(true))
        assertEquals("km", DisplayUtils.distanceUnit(false))
    }

    // ==================== Temperature Formatting ====================

    @Test
    fun `formatTemperature metric returns celsius`() {
        assertEquals("35\u00B0C", DisplayUtils.formatTemperature(35.0, useFahrenheit = false))
    }

    @Test
    fun `formatTemperature imperial converts to fahrenheit`() {
        assertEquals("95\u00B0F", DisplayUtils.formatTemperature(35.0, useFahrenheit = true))
    }

    @Test
    fun `formatTemperature freezing point`() {
        assertEquals("32\u00B0F", DisplayUtils.formatTemperature(0.0, useFahrenheit = true))
    }

    @Test
    fun `temperatureUnit returns correct unit`() {
        assertEquals("\u00B0F", DisplayUtils.temperatureUnit(true))
        assertEquals("\u00B0C", DisplayUtils.temperatureUnit(false))
    }

    // ==================== Duration Formatting ====================

    @Test
    fun `formatDurationShort hours and minutes`() {
        assertEquals("2h 10m", DisplayUtils.formatDurationShort(130))
    }

    @Test
    fun `formatDurationShort minutes only`() {
        assertEquals("45m", DisplayUtils.formatDurationShort(45))
    }

    @Test
    fun `formatDurationShort zero`() {
        assertEquals("0m", DisplayUtils.formatDurationShort(0))
    }

    // ==================== Wheel Settings Text ====================

    @Test
    fun `pedalsModeText maps modes correctly`() {
        assertEquals("Hard", DisplayUtils.pedalsModeText(0))
        assertEquals("Medium", DisplayUtils.pedalsModeText(1))
        assertEquals("Soft", DisplayUtils.pedalsModeText(2))
        assertEquals("Unknown", DisplayUtils.pedalsModeText(99))
    }

    @Test
    fun `lightModeText maps modes correctly`() {
        assertEquals("Off", DisplayUtils.lightModeText(0))
        assertEquals("On", DisplayUtils.lightModeText(1))
        assertEquals("Strobe", DisplayUtils.lightModeText(2))
        assertEquals("Unknown", DisplayUtils.lightModeText(99))
    }

    @Test
    fun `tiltBackSpeedText off when zero`() {
        assertEquals("Off", DisplayUtils.tiltBackSpeedText(0, useMph = false))
    }

    @Test
    fun `tiltBackSpeedText metric`() {
        assertEquals("35 km/h", DisplayUtils.tiltBackSpeedText(35, useMph = false))
    }

    @Test
    fun `tiltBackSpeedText imperial`() {
        assertEquals("22 mph", DisplayUtils.tiltBackSpeedText(35, useMph = true))
    }

    // ==================== Wheel Display Name ====================

    @Test
    fun `wheelDisplayName brand plus model`() {
        assertEquals("KingSong KS-S18", DisplayUtils.wheelDisplayName(WheelType.KINGSONG, "KS-S18", ""))
    }

    @Test
    fun `wheelDisplayName brand dedup`() {
        assertEquals(
            "KingSong KS-16X",
            DisplayUtils.wheelDisplayName(WheelType.KINGSONG, "KingSong KS-16X", "")
        )
    }

    @Test
    fun `wheelDisplayName fallback to Dashboard`() {
        assertEquals("Dashboard", DisplayUtils.wheelDisplayName(WheelType.Unknown, "", ""))
    }

    @Test
    fun `wheelDisplayName brand only`() {
        assertEquals("Veteran", DisplayUtils.wheelDisplayName(WheelType.VETERAN, "", ""))
    }

    @Test
    fun `wheelDisplayName name fallback when model empty`() {
        assertEquals("MyWheel", DisplayUtils.wheelDisplayName(WheelType.Unknown, "", "MyWheel"))
    }

    @Test
    fun `wheelDisplayName brand plus name when model empty`() {
        assertEquals(
            "Begode Monster",
            DisplayUtils.wheelDisplayName(WheelType.GOTWAY, "", "Monster")
        )
    }

    @Test
    fun `wheelDisplayName falls back to btName`() {
        assertEquals(
            "Begode GW-12345",
            DisplayUtils.wheelDisplayName(WheelType.GOTWAY, "", "", "GW-12345")
        )
    }

    @Test
    fun `wheelDisplayName prefers model over btName`() {
        assertEquals(
            "Begode MCM5",
            DisplayUtils.wheelDisplayName(WheelType.GOTWAY, "MCM5", "", "GW-12345")
        )
    }

    // ==================== RSSI ====================

    @Test
    fun `signalBars returns correct bar count`() {
        assertEquals(4, DisplayUtils.signalBars(-45))
        assertEquals(3, DisplayUtils.signalBars(-55))
        assertEquals(2, DisplayUtils.signalBars(-65))
        assertEquals(1, DisplayUtils.signalBars(-80))
    }

    @Test
    fun `signalBars boundary values`() {
        assertEquals(4, DisplayUtils.signalBars(-50))
        assertEquals(3, DisplayUtils.signalBars(-60))
        assertEquals(2, DisplayUtils.signalBars(-70))
        assertEquals(1, DisplayUtils.signalBars(-71))
    }

    @Test
    fun `signalDescription returns correct labels`() {
        assertEquals("Excellent", DisplayUtils.signalDescription(-45))
        assertEquals("Good", DisplayUtils.signalDescription(-55))
        assertEquals("Fair", DisplayUtils.signalDescription(-65))
        assertEquals("Weak", DisplayUtils.signalDescription(-80))
    }

    // ==================== WheelState.displayName ====================

    @Test
    fun `WheelState displayName with model`() {
        val state = WheelState(wheelType = WheelType.KINGSONG, model = "KS-S18")
        assertEquals("KingSong KS-S18", state.displayName)
    }

    @Test
    fun `WheelState displayName brand dedup`() {
        val state = WheelState(wheelType = WheelType.KINGSONG, model = "KingSong KS-16X")
        assertEquals("KingSong KS-16X", state.displayName)
    }

    @Test
    fun `WheelState displayName fallback to Dashboard`() {
        val state = WheelState()
        assertEquals("Dashboard", state.displayName)
    }

    @Test
    fun `WheelState displayName brand only`() {
        val state = WheelState(wheelType = WheelType.VETERAN)
        assertEquals("Veteran", state.displayName)
    }

    @Test
    fun `WheelState displayName falls back to btName`() {
        val state = WheelState(wheelType = WheelType.GOTWAY, btName = "GW-12345")
        assertEquals("Begode GW-12345", state.displayName)
    }

    @Test
    fun `WheelState displayName prefers model over btName`() {
        val state = WheelState(wheelType = WheelType.GOTWAY, model = "MCM5", btName = "GW-12345")
        assertEquals("Begode MCM5", state.displayName)
    }

    @Test
    fun `WheelState displayName brand only when btName also empty`() {
        val state = WheelState(wheelType = WheelType.GOTWAY)
        assertEquals("Begode", state.displayName)
    }

    // ==================== AlarmType.displayName ====================

    @Test
    fun `AlarmType displayName maps all values`() {
        assertEquals("Speed 1", AlarmType.SPEED1.displayName)
        assertEquals("Speed 2", AlarmType.SPEED2.displayName)
        assertEquals("Speed 3", AlarmType.SPEED3.displayName)
        assertEquals("Current", AlarmType.CURRENT.displayName)
        assertEquals("Temp", AlarmType.TEMPERATURE.displayName)
        assertEquals("PWM", AlarmType.PWM.displayName)
        assertEquals("Battery", AlarmType.BATTERY.displayName)
        assertEquals("Wheel", AlarmType.WHEEL.displayName)
    }
}
