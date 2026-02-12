package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.protocol.DefaultWheelDecoderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
}
