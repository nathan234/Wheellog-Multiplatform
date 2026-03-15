package org.freewheel.core.charger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargerStateTest {

    @Test
    fun defaults_allZeroAndFalse() {
        val state = ChargerState()
        assertEquals(0f, state.dcVoltage)
        assertEquals(0f, state.dcCurrent)
        assertEquals(0f, state.acVoltage)
        assertEquals(0f, state.acCurrent)
        assertEquals(0f, state.acFrequency)
        assertEquals(0f, state.temperature1)
        assertEquals(0f, state.temperature2)
        assertEquals(0f, state.currentLimitingPoint)
        assertEquals(0f, state.efficiency)
        assertFalse(state.isOutputEnabled)
        assertEquals(0f, state.targetVoltage)
        assertEquals(0f, state.targetCurrent)
        assertEquals("", state.firmwareVersion)
        assertFalse(state.isAuthenticated)
        assertEquals(0L, state.lastUpdateMs)
    }

    @Test
    fun dcPower_calculated() {
        val state = ChargerState(dcVoltage = 84f, dcCurrent = 5f)
        assertEquals(420f, state.dcPower)
    }

    @Test
    fun acPower_calculated() {
        val state = ChargerState(acVoltage = 230f, acCurrent = 2.5f)
        assertEquals(575f, state.acPower)
    }

    @Test
    fun isCharging_aboveThreshold() {
        assertTrue(ChargerState(dcCurrent = 0.2f).isCharging)
        assertTrue(ChargerState(dcCurrent = 5f).isCharging)
    }

    @Test
    fun isCharging_belowThreshold() {
        assertFalse(ChargerState(dcCurrent = 0.05f).isCharging)
        assertFalse(ChargerState(dcCurrent = 0.1f).isCharging)
        assertFalse(ChargerState(dcCurrent = 0f).isCharging)
    }

    @Test
    fun copy_preservesFields() {
        val state = ChargerState(
            dcVoltage = 84f,
            firmwareVersion = "v1.0",
            isAuthenticated = true
        )
        val updated = state.copy(dcCurrent = 5f)
        assertEquals(84f, updated.dcVoltage)
        assertEquals("v1.0", updated.firmwareVersion)
        assertTrue(updated.isAuthenticated)
        assertEquals(5f, updated.dcCurrent)
    }
}
