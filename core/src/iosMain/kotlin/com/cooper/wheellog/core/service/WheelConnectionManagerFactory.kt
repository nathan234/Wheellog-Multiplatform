package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.protocol.DefaultWheelDecoderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val demoScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/**
 * iOS factory and helpers for WheelConnectionManager.
 * Handles CoroutineScope creation and provides Swift-friendly accessors.
 */
object WheelConnectionManagerFactory {

    /**
     * Create a WheelConnectionManager with default configuration.
     * The scope is created internally and tied to the main dispatcher.
     */
    fun create(bleManager: BleManager): WheelConnectionManager {
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        return WheelConnectionManager(
            bleManager = bleManager,
            decoderFactory = DefaultWheelDecoderFactory(),
            scope = scope
        )
    }

    /**
     * Get current wheel state from a WheelConnectionManager.
     * Swift-friendly accessor that avoids StateFlow.value access.
     */
    fun getWheelState(manager: WheelConnectionManager): WheelState {
        return manager.wheelState.value
    }

    /**
     * Get current connection state from a WheelConnectionManager.
     * Swift-friendly accessor that avoids StateFlow.value access.
     */
    fun getConnectionState(manager: WheelConnectionManager): ConnectionState {
        return manager.connectionState.value
    }

    /**
     * Check if the manager is currently connected.
     */
    fun isConnected(manager: WheelConnectionManager): Boolean {
        return manager.connectionState.value.isConnected
    }

    fun sendBeep(manager: WheelConnectionManager) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.wheelBeep()
        }
    }

    fun sendToggleLight(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.toggleLight(enabled)
        }
    }

    fun sendSetPedalsMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setPedalsMode(mode)
        }
    }

    // MARK: - Lighting

    fun sendSetLightMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLightMode(mode)
        }
    }

    fun sendSetLed(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLed(enabled)
        }
    }

    fun sendSetLedMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLedMode(mode)
        }
    }

    fun sendSetStrobeMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setStrobeMode(mode)
        }
    }

    fun sendSetTailLight(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setTailLight(enabled)
        }
    }

    fun sendSetDrl(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setDrl(enabled)
        }
    }

    fun sendSetLedColor(manager: WheelConnectionManager, value: Int, ledNum: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLedColor(value, ledNum)
        }
    }

    fun sendSetLightBrightness(manager: WheelConnectionManager, value: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLightBrightness(value)
        }
    }

    // MARK: - Speed & Alarms

    fun sendSetMaxSpeed(manager: WheelConnectionManager, speed: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setMaxSpeed(speed)
        }
    }

    fun sendSetAlarmSpeed(manager: WheelConnectionManager, speed: Int, num: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setAlarmSpeed(speed, num)
        }
    }

    fun sendSetAlarmEnabled(manager: WheelConnectionManager, enabled: Boolean, num: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setAlarmEnabled(enabled, num)
        }
    }

    fun sendSetLimitedMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLimitedMode(enabled)
        }
    }

    fun sendSetLimitedSpeed(manager: WheelConnectionManager, speed: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLimitedSpeed(speed)
        }
    }

    fun sendSetAlarmMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setAlarmMode(mode)
        }
    }

    fun sendSetKingsongAlarms(manager: WheelConnectionManager, a1: Int, a2: Int, a3: Int, max: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setKingsongAlarms(a1, a2, a3, max)
        }
    }

    fun sendRequestAlarmSettings(manager: WheelConnectionManager) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.requestAlarmSettings()
        }
    }

    // MARK: - Ride Modes

    fun sendSetHandleButton(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setHandleButton(enabled)
        }
    }

    fun sendSetBrakeAssist(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setBrakeAssist(enabled)
        }
    }

    fun sendSetTransportMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setTransportMode(enabled)
        }
    }

    fun sendSetRideMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setRideMode(enabled)
        }
    }

    fun sendSetGoHomeMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setGoHomeMode(enabled)
        }
    }

    fun sendSetFancierMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setFancierMode(enabled)
        }
    }

    fun sendSetRollAngleMode(manager: WheelConnectionManager, mode: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setRollAngleMode(mode)
        }
    }

    // MARK: - Audio

    fun sendSetMute(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setMute(enabled)
        }
    }

    fun sendSetSpeakerVolume(manager: WheelConnectionManager, volume: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setSpeakerVolume(volume)
        }
    }

    fun sendSetBeeperVolume(manager: WheelConnectionManager, volume: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setBeeperVolume(volume)
        }
    }

    // MARK: - Thermal

    fun sendSetFanQuiet(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setFanQuiet(enabled)
        }
    }

    fun sendSetFan(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setFan(enabled)
        }
    }

    // MARK: - Pedal Tuning

    fun sendSetPedalTilt(manager: WheelConnectionManager, angle: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setPedalTilt(angle)
        }
    }

    fun sendSetPedalSensitivity(manager: WheelConnectionManager, sensitivity: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setPedalSensitivity(sensitivity)
        }
    }

    // MARK: - System

    fun sendCalibrate(manager: WheelConnectionManager) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.calibrate()
        }
    }

    fun sendPowerOff(manager: WheelConnectionManager) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.powerOff()
        }
    }

    fun sendSetLock(manager: WheelConnectionManager, locked: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setLock(locked)
        }
    }

    fun sendResetTrip(manager: WheelConnectionManager) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.resetTrip()
        }
    }

    fun sendSetMilesMode(manager: WheelConnectionManager, enabled: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            manager.setMilesMode(enabled)
        }
    }

    // MARK: - Demo Data Provider

    fun createDemoProvider(): DemoDataProvider {
        return DemoDataProvider()
    }

    fun startDemo(provider: DemoDataProvider) {
        provider.start(demoScope)
    }

    fun stopDemo(provider: DemoDataProvider) {
        provider.stop()
    }

    fun getDemoState(provider: DemoDataProvider): WheelState {
        return provider.wheelState.value
    }
}
