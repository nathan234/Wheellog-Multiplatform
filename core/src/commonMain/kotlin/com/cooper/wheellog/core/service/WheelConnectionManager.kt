package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.protocol.DecoderConfig
import com.cooper.wheellog.core.protocol.WheelCommand
import com.cooper.wheellog.core.protocol.WheelDecoder
import com.cooper.wheellog.core.protocol.WheelDecoderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central manager for wheel connections.
 * Coordinates BLE communication, protocol decoding, and state management.
 *
 * This is the main entry point for interacting with a wheel from the UI layer.
 */
class WheelConnectionManager(
    private val bleManager: BleManager,
    private val decoderFactory: WheelDecoderFactory
) {
    private val _wheelState = MutableStateFlow(WheelState())
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private var currentDecoder: WheelDecoder? = null
    private var decoderConfig = DecoderConfig()

    /**
     * Current wheel state as an observable flow.
     */
    val wheelState: StateFlow<WheelState> = _wheelState.asStateFlow()

    /**
     * Current connection state as an observable flow.
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Update decoder configuration.
     * Call this when user settings change.
     */
    fun updateConfig(config: DecoderConfig) {
        decoderConfig = config
    }

    /**
     * Connect to a wheel at the given address.
     *
     * @param address BLE MAC address (Android) or peripheral identifier (iOS)
     * @param wheelType Optional wheel type hint; if null, will be auto-detected
     */
    suspend fun connect(address: String, wheelType: WheelType? = null) {
        _connectionState.value = ConnectionState.Connecting(address)

        try {
            val connection = bleManager.connect(address)

            if (connection.isSuccess) {
                _connectionState.value = ConnectionState.DiscoveringServices(address)

                // If wheel type is known, set up decoder immediately
                wheelType?.let { type ->
                    currentDecoder = decoderFactory.createDecoder(type)
                    currentDecoder?.getInitCommands()?.forEach { cmd ->
                        sendCommand(cmd)
                    }
                }

                // Start receiving data
                // The actual data handling will be implemented in platform-specific code
            } else {
                _connectionState.value = ConnectionState.Failed(
                    connection.exceptionOrNull()?.message ?: "Unknown error"
                )
            }
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Connection failed")
        }
    }

    /**
     * Disconnect from the current wheel.
     */
    suspend fun disconnect() {
        currentDecoder?.reset()
        currentDecoder = null
        bleManager.disconnect()
        _wheelState.value = WheelState()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Send a command to the wheel.
     */
    suspend fun sendCommand(command: WheelCommand) {
        when (command) {
            is WheelCommand.SendBytes -> bleManager.write(command.data)
            is WheelCommand.SendDelayed -> {
                // Platform-specific delay implementation
                bleManager.write(command.data)
            }
            is WheelCommand.Beep -> currentDecoder?.let {
                // Convert to protocol-specific beep command
            }
            else -> {
                // Handle other commands
            }
        }
    }

    /**
     * Process incoming data from the wheel.
     * Called by platform-specific BLE implementation when data is received.
     */
    fun onDataReceived(data: ByteArray) {
        currentDecoder?.let { decoder ->
            val result = decoder.decode(data, _wheelState.value, decoderConfig)
            result?.let { decoded ->
                _wheelState.value = decoded.newState

                // Send any response commands
                decoded.commands.forEach { cmd ->
                    // Queue command for sending
                }

                // Update connection state if needed
                if (decoder.isReady() && _connectionState.value !is ConnectionState.Connected) {
                    val address = (_connectionState.value as? ConnectionState.DiscoveringServices)?.address ?: ""
                    _connectionState.value = ConnectionState.Connected(
                        address = address,
                        wheelName = decoded.newState.name.ifEmpty { decoded.newState.model }
                    )
                }
            }
        }
    }

    /**
     * Handle wheel type detection.
     * Called by platform-specific code after BLE service discovery.
     */
    fun onWheelTypeDetected(wheelType: WheelType) {
        currentDecoder = decoderFactory.createDecoder(wheelType)
        _wheelState.value = _wheelState.value.copy(wheelType = wheelType)
    }

    /**
     * Play beep on the connected wheel.
     */
    suspend fun wheelBeep() {
        sendCommand(WheelCommand.Beep)
    }

    /**
     * Toggle light on the connected wheel.
     */
    suspend fun toggleLight(enabled: Boolean) {
        sendCommand(WheelCommand.SetLight(enabled))
    }
}

/**
 * Platform-specific BLE manager interface.
 * Implement this using expect/actual for each platform.
 */
expect class BleManager {
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect(address: String): Result<BleConnection>
    suspend fun disconnect()
    suspend fun write(data: ByteArray): Boolean
    suspend fun startScan(onDeviceFound: (BleDevice) -> Unit)
    suspend fun stopScan()
}

/**
 * Represents a BLE connection to a wheel.
 */
expect class BleConnection

/**
 * Represents a discovered BLE device.
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int
)
