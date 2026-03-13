package org.freewheel.core.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class BluetoothAdapterStateTest {

    // ==================== isReady ====================

    @Test
    fun `POWERED_ON is ready`() {
        assertTrue(BluetoothAdapterState.POWERED_ON.isReady)
    }

    @Test
    fun `POWERED_OFF is not ready`() {
        assertFalse(BluetoothAdapterState.POWERED_OFF.isReady)
    }

    @Test
    fun `UNAUTHORIZED is not ready`() {
        assertFalse(BluetoothAdapterState.UNAUTHORIZED.isReady)
    }

    @Test
    fun `UNSUPPORTED is not ready`() {
        assertFalse(BluetoothAdapterState.UNSUPPORTED.isReady)
    }

    @Test
    fun `UNKNOWN is not ready`() {
        assertFalse(BluetoothAdapterState.UNKNOWN.isReady)
    }

    @Test
    fun `RESETTING is not ready`() {
        assertFalse(BluetoothAdapterState.RESETTING.isReady)
    }

    // ==================== BleManagerPort default ====================

    @Test
    fun `BleManagerPort default bluetoothState is POWERED_ON`() {
        val fake = FakeBleManager()
        assertEquals(BluetoothAdapterState.POWERED_ON, fake.bluetoothState.value)
    }
}
