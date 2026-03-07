package org.freewheel.core.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.freewheel.core.protocol.DefaultWheelDecoderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for connection cancellation — verifying that disconnect() during
 * a pending connect() unblocks the coroutine and transitions to Disconnected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WheelConnectionManagerCancelTest {

    private fun TestScope.createManager(fakeBle: FakeBleManager): WheelConnectionManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return WheelConnectionManager(fakeBle, DefaultWheelDecoderFactory(), this, dispatcher)
    }

    @Test
    fun disconnectDuringConnectResolvesImmediately() = runTest {
        val fakeBle = FakeBleManager()
        fakeBle.connectDeferred = CompletableDeferred()
        val manager = createManager(fakeBle)

        // Start connect — will suspend on the deferred
        val connectJob = launch { manager.connect("AA:BB:CC:DD:EE:FF") }
        runCurrent()
        assertEquals(ConnectionState.Connecting("AA:BB:CC:DD:EE:FF"), manager.connectionState.value)

        // Simulate disconnect while connecting — complete deferred first (BLE cancel returns false)
        fakeBle.connectDeferred!!.complete(false)
        manager.disconnect()
        runCurrent()

        // Connect coroutine should have completed (not hanging)
        assertTrue(connectJob.isCompleted)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun disconnectDuringConnectDoesNotTransitionToFailed() = runTest {
        val fakeBle = FakeBleManager()
        fakeBle.connectDeferred = CompletableDeferred()
        val manager = createManager(fakeBle)

        val connectJob = launch { manager.connect("AA:BB:CC:DD:EE:FF") }
        runCurrent()

        // Disconnect sets state to Disconnected before connect resumes
        // When connect resumes with false, it should NOT overwrite to Failed
        manager.disconnect()
        fakeBle.connectDeferred!!.complete(false)
        runCurrent()

        assertTrue(connectJob.isCompleted)
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
}
