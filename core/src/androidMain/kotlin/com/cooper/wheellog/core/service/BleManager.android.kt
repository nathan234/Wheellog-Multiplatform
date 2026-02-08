package com.cooper.wheellog.core.service

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.cooper.wheellog.core.ble.DiscoveredService
import com.cooper.wheellog.core.ble.DiscoveredServices
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.WriteType
import com.welie.blessed.ConnectionState as BlessedConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Android implementation of BleManager using the blessed-android library.
 *
 * This manager handles:
 * - BLE scanning for wheel devices
 * - Connection management
 * - Service discovery
 * - Characteristic read/write operations
 * - Connection state tracking
 *
 * ## Usage
 *
 * The manager can be used in two modes:
 *
 * ### Standalone Mode (Recommended for new code)
 * Initialize with a BluetoothCentralManager instance:
 * ```
 * val central = BluetoothCentralManager(context, centralCallback, handler)
 * val bleManager = BleManager()
 * bleManager.initialize(central)
 * bleManager.connect(address)
 * ```
 *
 * ### Bridge Mode (For incremental migration)
 * Set an existing peripheral directly:
 * ```
 * val bleManager = BleManager()
 * bleManager.setPeripheral(existingPeripheral)
 * ```
 */
actual class BleManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var central: BluetoothCentralManager? = null
    private var currentPeripheral: BluetoothPeripheral? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var connectionResult = Channel<Result<BleConnection>>(1)
    private var scope = CoroutineScope(Dispatchers.Main)

    // Callback for data received from the wheel
    private var onDataReceivedCallback: ((ByteArray) -> Unit)? = null

    // Callback for services discovered
    private var onServicesDiscoveredCallback: ((DiscoveredServices, String?) -> Unit)? = null

    actual val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    /**
     * Initialize with a BluetoothCentralManager.
     * Must be called before connect() in standalone mode.
     */
    fun initialize(centralManager: BluetoothCentralManager) {
        this.central = centralManager
    }

    /**
     * Set callback for when data is received from the wheel.
     */
    fun setDataReceivedCallback(callback: (ByteArray) -> Unit) {
        onDataReceivedCallback = callback
    }

    /**
     * Set callback for when services are discovered.
     */
    fun setServicesDiscoveredCallback(callback: (DiscoveredServices, String?) -> Unit) {
        onServicesDiscoveredCallback = callback
    }

    /**
     * Bridge mode: Set an existing peripheral for use with legacy code.
     * This allows incremental migration from existing BluetoothService.
     */
    fun setPeripheral(
        peripheral: BluetoothPeripheral,
        writeChar: BluetoothGattCharacteristic? = null,
        readChar: BluetoothGattCharacteristic? = null
    ) {
        currentPeripheral = peripheral
        writeCharacteristic = writeChar
        readCharacteristic = readChar

        _connectionState.value = when (peripheral.state) {
            BlessedConnectionState.CONNECTED -> ConnectionState.Connected(
                address = peripheral.address,
                wheelName = peripheral.name ?: "Unknown"
            )
            BlessedConnectionState.CONNECTING -> ConnectionState.Connecting(peripheral.address)
            BlessedConnectionState.DISCONNECTING -> ConnectionState.Disconnected
            BlessedConnectionState.DISCONNECTED -> ConnectionState.Disconnected
        }
    }

    /**
     * Get the current peripheral.
     * Used for advanced operations or migration support.
     */
    fun getPeripheral(): BluetoothPeripheral? = currentPeripheral

    /**
     * Get discovered services from the current peripheral.
     */
    fun getServices(): List<BluetoothGattService>? = currentPeripheral?.services

    /**
     * Get a specific service by UUID.
     */
    fun getService(uuid: UUID): BluetoothGattService? = currentPeripheral?.getService(uuid)

    private val peripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            _connectionState.value = ConnectionState.DiscoveringServices(peripheral.address)

            // Convert to platform-agnostic DiscoveredServices
            val discoveredServices = peripheral.services.map { service ->
                DiscoveredService(
                    uuid = service.uuid.toString(),
                    characteristics = service.characteristics.map { it.uuid.toString() }
                )
            }

            onServicesDiscoveredCallback?.invoke(
                DiscoveredServices(discoveredServices),
                peripheral.name
            )
        }

        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            if (status == GattStatus.SUCCESS) {
                onDataReceivedCallback?.invoke(value)
            }
        }

        override fun onCharacteristicWrite(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            // Write completed - could track for reliability
        }

        override fun onMtuChanged(
            peripheral: BluetoothPeripheral,
            mtu: Int,
            status: GattStatus
        ) {
            // MTU changed - useful for extended frames
        }
    }

    actual suspend fun connect(address: String): Result<BleConnection> {
        val manager = central ?: return Result.failure(
            IllegalStateException("BleManager not initialized. Call initialize() first.")
        )

        _connectionState.value = ConnectionState.Connecting(address)

        return try {
            val peripheral = manager.getPeripheral(address)
            currentPeripheral = peripheral

            manager.connectPeripheral(peripheral, peripheralCallback)

            // Wait for connection with timeout
            val connected = withTimeoutOrNull(30000L) {
                while (peripheral.state != BlessedConnectionState.CONNECTED) {
                    kotlinx.coroutines.delay(100)
                }
                true
            }

            if (connected != true) {
                return Result.failure(Exception("Connection timeout after 30 seconds"))
            }

            // Request MTU for extended frames
            peripheral.requestMtu(BluetoothPeripheral.MAX_MTU)

            _connectionState.value = ConnectionState.Connected(
                address = address,
                wheelName = peripheral.name ?: "Unknown"
            )

            Result.success(BleConnection(peripheral))
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Failed(e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    actual suspend fun disconnect() {
        currentPeripheral?.let { peripheral ->
            central?.cancelConnection(peripheral)
        }
        currentPeripheral = null
        writeCharacteristic = null
        readCharacteristic = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Write data to the wheel using the configured write characteristic.
     *
     * @param data The data to write
     * @return true if write was successful
     */
    actual suspend fun write(data: ByteArray): Boolean {
        val peripheral = currentPeripheral ?: return false
        val characteristic = writeCharacteristic ?: return false

        if (peripheral.state != BlessedConnectionState.CONNECTED) {
            return false
        }

        return peripheral.writeCharacteristic(
            characteristic,
            data,
            WriteType.WITHOUT_RESPONSE
        )
    }

    /**
     * Write data with chunking for protocols that need it (e.g., Inmotion V1).
     *
     * @param data The data to write
     * @param chunkSize Maximum chunk size (default 20 bytes)
     * @param delayMs Delay between chunks in milliseconds
     * @return true if all writes were successful
     */
    suspend fun writeChunked(data: ByteArray, chunkSize: Int = 20, delayMs: Long = 20): Boolean {
        val peripheral = currentPeripheral ?: return false
        val characteristic = writeCharacteristic ?: return false

        if (peripheral.state != BlessedConnectionState.CONNECTED) {
            return false
        }

        var offset = 0
        while (offset < data.size) {
            val chunk = data.copyOfRange(offset, minOf(offset + chunkSize, data.size))
            val success = peripheral.writeCharacteristic(
                characteristic,
                chunk,
                WriteType.WITHOUT_RESPONSE
            )
            if (!success) return false

            offset += chunkSize
            if (offset < data.size) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
        return true
    }

    /**
     * Configure the write characteristic for sending commands.
     */
    fun setWriteCharacteristic(serviceUuid: String, charUuid: String) {
        val peripheral = currentPeripheral ?: return
        val service = peripheral.getService(UUID.fromString(serviceUuid)) ?: return
        writeCharacteristic = service.getCharacteristic(UUID.fromString(charUuid))
    }

    /**
     * Configure the read characteristic and enable notifications.
     */
    fun setReadCharacteristic(serviceUuid: String, charUuid: String) {
        val peripheral = currentPeripheral ?: return
        val service = peripheral.getService(UUID.fromString(serviceUuid)) ?: return
        readCharacteristic = service.getCharacteristic(UUID.fromString(charUuid))

        // Enable notifications
        readCharacteristic?.let { char ->
            peripheral.setNotify(char, true)
        }
    }

    /**
     * Configure characteristics based on WheelConnectionInfo.
     */
    fun configureForWheel(
        readServiceUuid: String,
        readCharUuid: String,
        writeServiceUuid: String,
        writeCharUuid: String
    ) {
        setReadCharacteristic(readServiceUuid, readCharUuid)
        setWriteCharacteristic(writeServiceUuid, writeCharUuid)
    }

    actual suspend fun startScan(onDeviceFound: (BleDevice) -> Unit) {
        val manager = central ?: return

        // Note: In a full implementation, we would set up a scan callback
        // that filters for wheel-related services and calls onDeviceFound
        // for each discovered device.

        // For now, this is a placeholder that would need integration
        // with the app layer's scan implementation.
    }

    actual suspend fun stopScan() {
        central?.stopScan()
    }
}

/**
 * Android BLE connection wrapper.
 * Wraps a BluetoothPeripheral for type-safe connection handling.
 */
actual class BleConnection(
    val peripheral: BluetoothPeripheral
) {
    val address: String get() = peripheral.address
    val name: String? get() = peripheral.name
    val isConnected: Boolean get() = peripheral.state == BlessedConnectionState.CONNECTED
}
