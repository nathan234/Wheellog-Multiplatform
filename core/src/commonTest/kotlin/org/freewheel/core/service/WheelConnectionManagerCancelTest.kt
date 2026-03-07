package org.freewheel.core.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.freewheel.core.protocol.DefaultWheelDecoderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for connection cancellation — verifying that disconnect() during
 * a pending connect() transitions to Disconnected without hitting Failed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WheelConnectionManagerCancelTest {

    private fun TestScope.createManager(fakeBle: FakeBleManager): WheelConnectionManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return WheelConnectionManager(fakeBle, DefaultWheelDecoderFactory(), backgroundScope, dispatcher)
    }

    @Test
    fun disconnectDuringConnectResolvesImmediately() = runTest(timeout = 0.1.seconds) {
        val fakeBle = FakeBleManager()
        fakeBle.connectDeferred = CompletableDeferred()
        val manager = createManager(fakeBle)

        // Fire connect — event loop sets state to Connecting and launches BLE job
        manager.connect("AA:BB:CC:DD:EE:FF")
        runCurrent()
        assertEquals(ConnectionState.Connecting("AA:BB:CC:DD:EE:FF"), manager.connectionState.value)

        // BLE connect completes with failure, then disconnect fires
        fakeBle.connectDeferred!!.complete(false)
        manager.disconnect()
        runCurrent()

        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }

    @Test
    fun disconnectDuringConnectDoesNotTransitionToFailed() = runTest(timeout = 0.1.seconds) {
        val fakeBle = FakeBleManager()
        fakeBle.connectDeferred = CompletableDeferred()
        val manager = createManager(fakeBle)

        manager.connect("AA:BB:CC:DD:EE:FF")
        runCurrent()

        // Disconnect fires before BLE connect resolves
        manager.disconnect()
        runCurrent()
        fakeBle.connectDeferred!!.complete(false)
        runCurrent()

        // Should be Disconnected, NOT Failed
        assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
    }
}
