package com.cooper.wheellog.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS implementation of BleManager.
 * This is a placeholder that will be implemented using CoreBluetooth via Kotlin/Native interop.
 */
actual class BleManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    actual val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    actual suspend fun connect(address: String): Result<BleConnection> {
        // TODO: Implement using CoreBluetooth
        // Will use CBCentralManager and CBPeripheral
        return Result.failure(NotImplementedError("iOS BLE connection not yet implemented"))
    }

    actual suspend fun disconnect() {
        // TODO: Implement
        _connectionState.value = ConnectionState.Disconnected
    }

    actual suspend fun write(data: ByteArray): Boolean {
        // TODO: Implement using CBPeripheral.writeValue
        return false
    }

    actual suspend fun startScan(onDeviceFound: (BleDevice) -> Unit) {
        // TODO: Implement using CBCentralManager.scanForPeripherals
    }

    actual suspend fun stopScan() {
        // TODO: Implement
    }
}

/**
 * iOS BLE connection wrapper.
 * Will wrap CBPeripheral and its characteristics.
 */
actual class BleConnection {
    // Will hold reference to CBPeripheral
}
