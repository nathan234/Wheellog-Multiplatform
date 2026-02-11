package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.ble.DiscoveredServices
import com.cooper.wheellog.core.ble.WheelConnectionInfo
import com.cooper.wheellog.core.ble.WheelTypeDetector
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.protocol.DecoderConfig
import com.cooper.wheellog.core.protocol.WheelCommand
import com.cooper.wheellog.core.protocol.WheelDecoder
import com.cooper.wheellog.core.protocol.WheelDecoderFactory
import com.cooper.wheellog.core.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central manager for wheel connections.
 * Coordinates BLE communication, protocol decoding, and state management.
 *
 * This is the main entry point for interacting with a wheel from the UI layer.
 *
 * ## Usage
 * ```
 * val manager = WheelConnectionManager(bleManager, decoderFactory, scope)
 *
 * // Observe state
 * manager.wheelState.collect { state -> updateUI(state) }
 * manager.connectionState.collect { state -> updateConnectionUI(state) }
 *
 * // Connect
 * manager.connect(address, WheelType.KINGSONG)
 *
 * // Disconnect
 * manager.disconnect()
 * ```
 */
class WheelConnectionManager(
    private val bleManager: BleManager,
    private val decoderFactory: WheelDecoderFactory,
    private val scope: CoroutineScope
) {
    private val _wheelState = MutableStateFlow(WheelState())
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    private var currentDecoder: WheelDecoder? = null
    private var decoderConfig = DecoderConfig()
    private var connectionInfo: WheelConnectionInfo? = null

    private val wheelTypeDetector = WheelTypeDetector()
    private val keepAliveTimer = KeepAliveTimer(scope)
    private val dataTimeoutTracker = DataTimeoutTracker(scope)
    private val commandScheduler = CommandScheduler(scope)

    // Track last data time for timeout detection
    private var lastDataReceivedTime: Long = 0

    /**
     * Current wheel state as an observable flow.
     */
    val wheelState: StateFlow<WheelState> = _wheelState.asStateFlow()

    /**
     * Current connection state as an observable flow.
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * Whether the keep-alive timer is running.
     */
    val isKeepAliveRunning: StateFlow<Boolean> = keepAliveTimer.isRunning

    /**
     * Update decoder configuration.
     * Call this when user settings change.
     */
    fun updateConfig(config: DecoderConfig) {
        decoderConfig = config
    }

    /**
     * Get the current decoder configuration.
     */
    fun getConfig(): DecoderConfig = decoderConfig

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
                    setupDecoder(type)
                }

                // Start data timeout monitoring
                startDataTimeoutMonitor(address)

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
        stopTimers()
        commandScheduler.cancelAll()

        currentDecoder?.reset()
        currentDecoder = null
        connectionInfo = null

        bleManager.disconnect()
        _wheelState.value = WheelState()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Send a command to the wheel.
     */
    suspend fun sendCommand(command: WheelCommand) {
        when (command) {
            is WheelCommand.SendBytes -> {
                bleManager.write(command.data)
            }
            is WheelCommand.SendDelayed -> {
                commandScheduler.schedule(command.delayMs) {
                    bleManager.write(command.data)
                }
            }
            else -> {
                val rawCommands = currentDecoder?.buildCommand(command) ?: return
                for (cmd in rawCommands) {
                    when (cmd) {
                        is WheelCommand.SendBytes -> bleManager.write(cmd.data)
                        is WheelCommand.SendDelayed -> commandScheduler.schedule(cmd.delayMs) {
                            bleManager.write(cmd.data)
                        }
                        else -> {} // prevent recursion
                    }
                }
            }
        }
    }

    /**
     * Process incoming data from the wheel.
     * Called by platform-specific BLE implementation when data is received.
     */
    fun onDataReceived(data: ByteArray) {
        lastDataReceivedTime = currentTimeMillis()
        dataTimeoutTracker.onDataReceived()

        val decoder = currentDecoder
        if (decoder == null) {
            Logger.w("WheelConnectionManager", "Data received (${data.size} bytes) but no decoder set")
            return
        }

        val result = decoder.decode(data, _wheelState.value, decoderConfig)
        if (result == null) {
            Logger.d("WheelConnectionManager", "decode() returned null for ${data.size} bytes (decoder=${decoder.wheelType})")
            return
        }

        _wheelState.value = result.newState

        // Send any response commands
        if (result.commands.isNotEmpty()) {
            scope.launch {
                result.commands.forEach { cmd ->
                    sendCommand(cmd)
                }
            }
        }

        // Update connection state if decoder is ready
        if (decoder.isReady() && _connectionState.value !is ConnectionState.Connected) {
            val address = getCurrentAddress() ?: ""
            Logger.d("WheelConnectionManager", "Decoder ready, transitioning to Connected")
            _connectionState.value = ConnectionState.Connected(
                address = address,
                wheelName = result.newState.name.ifEmpty { result.newState.model }
            )

            // Start keep-alive timer now that we're fully connected
            startKeepAliveTimer()
        } else if (!decoder.isReady()) {
            Logger.d("WheelConnectionManager", "Decoded OK but isReady()=false (decoder=${decoder.wheelType})")
        }
    }

    /**
     * Handle BLE service discovery.
     * Called by platform-specific code after services are discovered.
     *
     * @param services The discovered BLE services
     * @param deviceName The device name for detection heuristics
     */
    fun onServicesDiscovered(services: DiscoveredServices, deviceName: String?) {
        Logger.d("WheelConnectionManager", "onServicesDiscovered: deviceName=$deviceName, services=${services.serviceUuids()}")
        val result = wheelTypeDetector.detect(services, deviceName)
        Logger.d("WheelConnectionManager", "Detection result: $result")

        when (result) {
            is WheelTypeDetector.DetectionResult.Detected -> {
                Logger.d("WheelConnectionManager", "Detected: ${result.wheelType}, read=${result.readServiceUuid}/${result.readCharacteristicUuid}")
                connectionInfo = WheelConnectionInfo(
                    wheelType = result.wheelType,
                    readServiceUuid = result.readServiceUuid,
                    readCharacteristicUuid = result.readCharacteristicUuid,
                    writeServiceUuid = result.writeServiceUuid,
                    writeCharacteristicUuid = result.writeCharacteristicUuid
                )
                setupDecoder(result.wheelType)
            }
            is WheelTypeDetector.DetectionResult.Ambiguous -> {
                Logger.d("WheelConnectionManager", "Ambiguous: ${result.possibleTypes}, using GOTWAY_VIRTUAL")
                connectionInfo = wheelTypeDetector.getUuidsForType(WheelType.GOTWAY_VIRTUAL)
                setupDecoder(WheelType.GOTWAY_VIRTUAL)
            }
            is WheelTypeDetector.DetectionResult.Unknown -> {
                Logger.w("WheelConnectionManager", "Unknown wheel: ${result.reason}")
                _connectionState.value = ConnectionState.Failed(
                    "Unknown wheel type: ${result.reason}"
                )
            }
        }
    }

    /**
     * Handle wheel type detection.
     * Called when wheel type is determined (either from services or auto-detect).
     */
    fun onWheelTypeDetected(wheelType: WheelType) {
        setupDecoder(wheelType)
        _wheelState.value = _wheelState.value.copy(wheelType = wheelType)

        // Update connection info if we don't have it
        if (connectionInfo == null) {
            connectionInfo = wheelTypeDetector.getUuidsForType(wheelType)
        }
    }

    /**
     * Get the connection info for the current wheel.
     * Returns null if not connected or wheel type unknown.
     */
    fun getConnectionInfo(): WheelConnectionInfo? = connectionInfo

    /**
     * Get the current decoder.
     * Exposed for testing and advanced use cases.
     */
    fun getCurrentDecoder(): WheelDecoder? = currentDecoder

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

    // ==================== Private Methods ====================

    private fun setupDecoder(wheelType: WheelType) {
        currentDecoder?.reset()
        currentDecoder = decoderFactory.createDecoder(wheelType)
        _wheelState.value = _wheelState.value.copy(wheelType = wheelType)

        // Send init commands
        currentDecoder?.getInitCommands()?.let { commands ->
            scope.launch {
                commands.forEach { cmd ->
                    sendCommand(cmd)
                }
            }
        }
    }

    private fun startKeepAliveTimer() {
        val decoder = currentDecoder ?: return
        val intervalMs = decoder.keepAliveIntervalMs

        if (intervalMs <= 0) {
            // This wheel doesn't need keep-alive (e.g., Gotway, Kingsong)
            return
        }

        keepAliveTimer.start(
            intervalMs = intervalMs,
            initialDelayMs = intervalMs // Wait one interval before first tick
        ) {
            decoder.getKeepAliveCommand()?.let { command ->
                sendCommand(command)
            }
        }
    }

    private fun startDataTimeoutMonitor(address: String) {
        dataTimeoutTracker.start(
            timeoutMs = DataTimeoutTracker.DEFAULT_TIMEOUT_MS
        ) {
            // Data timeout occurred - connection may be lost
            _connectionState.value = ConnectionState.ConnectionLost(
                address = address,
                reason = "No data received for ${DataTimeoutTracker.DEFAULT_TIMEOUT_MS / 1000} seconds"
            )
            stopTimers()
        }
    }

    private fun stopTimers() {
        keepAliveTimer.stop()
        dataTimeoutTracker.stop()
    }

    private fun getCurrentAddress(): String? {
        return when (val state = _connectionState.value) {
            is ConnectionState.Connecting -> state.address
            is ConnectionState.DiscoveringServices -> state.address
            is ConnectionState.Connected -> state.address
            is ConnectionState.ConnectionLost -> state.address
            else -> null
        }
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
