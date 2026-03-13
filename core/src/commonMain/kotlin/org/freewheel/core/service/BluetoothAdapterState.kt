package org.freewheel.core.service

/**
 * Platform-agnostic Bluetooth adapter state.
 *
 * Maps to CBManagerState on iOS and BluetoothAdapter.STATE_* on Android.
 * Separate from [ConnectionState] — this represents the radio/permission layer,
 * not the connection lifecycle.
 */
enum class BluetoothAdapterState {
    /** State not yet determined (e.g., CBCentralManager hasn't reported yet). */
    UNKNOWN,

    /** Bluetooth hardware is resetting. */
    RESETTING,

    /** Device does not support Bluetooth. */
    UNSUPPORTED,

    /** App lacks Bluetooth permission. */
    UNAUTHORIZED,

    /** Bluetooth radio is turned off. */
    POWERED_OFF,

    /** Bluetooth is on and ready to use. */
    POWERED_ON;

    /** Whether BLE operations (scan, connect) can proceed. */
    val isReady: Boolean get() = this == POWERED_ON
}
