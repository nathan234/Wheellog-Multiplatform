package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.ble.DiscoveredService
import com.cooper.wheellog.core.ble.DiscoveredServices
import kotlinx.cinterop.*
import kotlin.experimental.ExperimentalObjCName
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS implementation of BleManager using CoreBluetooth.
 *
 * This implementation uses Kotlin/Native interop with CoreBluetooth framework.
 *
 * ## Architecture
 *
 * The iOS BLE stack uses delegate-based callbacks:
 * - `CBCentralManagerDelegate` for connection management
 * - `CBPeripheralDelegate` for data and service discovery
 *
 * This class bridges the delegate pattern to Kotlin coroutines and StateFlow.
 *
 * ## Required Capabilities
 *
 * Add to Info.plist:
 * - `NSBluetoothAlwaysUsageDescription` - Permission description
 * - `UIBackgroundModes` with `bluetooth-central` for background operation
 */
@OptIn(ExperimentalForeignApi::class)
actual class BleManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // CoreBluetooth objects
    private var centralManager: CBCentralManager? = null
    private var currentPeripheral: CBPeripheral? = null
    private var writeCharacteristic: CBCharacteristic? = null
    private var readCharacteristic: CBCharacteristic? = null

    // UUID configuration
    private var readServiceUuid: String? = null
    private var readCharUuid: String? = null
    private var writeServiceUuid: String? = null
    private var writeCharUuid: String? = null

    // Callbacks
    private var onDataReceivedCallback: ((ByteArray) -> Unit)? = null
    private var onServicesDiscoveredCallback: ((DiscoveredServices, String?) -> Unit)? = null
    private var scanCallback: ((BleDevice) -> Unit)? = null

    // Connection continuation for suspend function
    private var connectionContinuation: CancellableContinuation<Result<BleConnection>>? = null

    // Delegate instances (prevent garbage collection)
    private var centralDelegate: CBCentralManagerDelegateImpl? = null
    private var peripheralDelegate: CBPeripheralDelegateImpl? = null

    actual val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    /**
     * Initialize the CBCentralManager.
     * Must be called before any BLE operations.
     */
    fun initialize() {
        if (centralManager == null) {
            centralDelegate = CBCentralManagerDelegateImpl(this)
            peripheralDelegate = CBPeripheralDelegateImpl(this)
            centralManager = CBCentralManager(delegate = centralDelegate, queue = null)
        }
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
     * Configure characteristics based on WheelConnectionInfo.
     */
    fun configureForWheel(
        readServiceUuid: String,
        readCharUuid: String,
        writeServiceUuid: String,
        writeCharUuid: String
    ) {
        this.readServiceUuid = readServiceUuid
        this.readCharUuid = readCharUuid
        this.writeServiceUuid = writeServiceUuid
        this.writeCharUuid = writeCharUuid
    }

    actual suspend fun connect(address: String): Result<BleConnection> {
        val central = centralManager ?: run {
            initialize()
            centralManager!!
        }

        // Check if Bluetooth is powered on
        if (central.state != CBManagerStatePoweredOn) {
            return Result.failure(IllegalStateException("Bluetooth is not powered on. State: ${central.state}"))
        }

        _connectionState.value = ConnectionState.Connecting(address)

        return suspendCancellableCoroutine { continuation ->
            connectionContinuation = continuation

            // Try to retrieve peripheral by UUID
            val uuid = NSUUID(uUIDString = address)
            val peripherals = central.retrievePeripheralsWithIdentifiers(listOf(uuid))

            if (peripherals.isNotEmpty()) {
                val peripheral = peripherals.first() as CBPeripheral
                currentPeripheral = peripheral
                peripheral.delegate = peripheralDelegate
                central.connectPeripheral(peripheral, options = null)
            } else {
                // Peripheral not found, need to scan
                continuation.resume(
                    Result.failure(Exception("Peripheral not found: $address"))
                ) {}
                connectionContinuation = null
            }

            continuation.invokeOnCancellation {
                currentPeripheral?.let { central.cancelPeripheralConnection(it) }
                connectionContinuation = null
            }
        }
    }

    actual suspend fun disconnect() {
        currentPeripheral?.let { peripheral ->
            centralManager?.cancelPeripheralConnection(peripheral)
        }
        currentPeripheral = null
        writeCharacteristic = null
        readCharacteristic = null
        _connectionState.value = ConnectionState.Disconnected
    }

    actual suspend fun write(data: ByteArray): Boolean {
        val peripheral = currentPeripheral ?: return false
        val characteristic = writeCharacteristic ?: return false

        if (peripheral.state != CBPeripheralStateConnected) {
            return false
        }

        val nsData = data.toNSData()
        peripheral.writeValue(nsData, characteristic, CBCharacteristicWriteWithoutResponse)
        return true
    }

    /**
     * Write data with chunking for protocols that need it (e.g., Inmotion V1).
     */
    suspend fun writeChunked(data: ByteArray, chunkSize: Int = 20, delayMs: Long = 20): Boolean {
        val peripheral = currentPeripheral ?: return false
        val characteristic = writeCharacteristic ?: return false

        if (peripheral.state != CBPeripheralStateConnected) {
            return false
        }

        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            val nsData = chunk.toNSData()
            peripheral.writeValue(nsData, characteristic, CBCharacteristicWriteWithoutResponse)

            offset += chunkSize
            if (offset < data.size) {
                delay(delayMs)
            }
        }
        return true
    }

    actual suspend fun startScan(onDeviceFound: (BleDevice) -> Unit) {
        val central = centralManager ?: run {
            initialize()
            centralManager!!
        }

        if (central.state != CBManagerStatePoweredOn) {
            return
        }

        scanCallback = onDeviceFound
        _connectionState.value = ConnectionState.Scanning

        // Scan for all peripherals (could filter by service UUIDs)
        central.scanForPeripheralsWithServices(serviceUUIDs = null, options = null)
    }

    actual suspend fun stopScan() {
        centralManager?.stopScan()
        scanCallback = null
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // ==================== Internal Callback Methods ====================

    internal fun onStateUpdated(state: Long) {
        when (state) {
            CBManagerStatePoweredOn -> {
                // Ready to use
            }
            CBManagerStatePoweredOff -> {
                _connectionState.value = ConnectionState.Failed("Bluetooth is powered off")
            }
            CBManagerStateUnauthorized -> {
                _connectionState.value = ConnectionState.Failed("Bluetooth permission denied")
            }
            CBManagerStateUnsupported -> {
                _connectionState.value = ConnectionState.Failed("Bluetooth not supported")
            }
            else -> {
                // Resetting, Unknown states
            }
        }
    }

    internal fun onPeripheralDiscovered(peripheral: CBPeripheral, rssi: Int) {
        val device = BleDevice(
            address = peripheral.identifier.UUIDString,
            name = peripheral.name,
            rssi = rssi
        )
        scanCallback?.invoke(device)
    }

    internal fun onPeripheralConnected(peripheral: CBPeripheral) {
        _connectionState.value = ConnectionState.DiscoveringServices(
            peripheral.identifier.UUIDString
        )
        // Discover all services
        peripheral.discoverServices(serviceUUIDs = null)
    }

    internal fun onConnectionFailed(peripheral: CBPeripheral, error: NSError?) {
        val errorMessage = error?.localizedDescription ?: "Connection failed"
        _connectionState.value = ConnectionState.Failed(errorMessage)

        connectionContinuation?.resume(Result.failure(Exception(errorMessage))) {}
        connectionContinuation = null
    }

    internal fun onPeripheralDisconnected(peripheral: CBPeripheral, error: NSError?) {
        val address = peripheral.identifier.UUIDString
        if (error != null) {
            _connectionState.value = ConnectionState.ConnectionLost(
                address = address,
                reason = error.localizedDescription ?: "Unknown error"
            )
        } else {
            _connectionState.value = ConnectionState.Disconnected
        }

        currentPeripheral = null
        writeCharacteristic = null
        readCharacteristic = null
    }

    internal fun onServicesDiscovered(peripheral: CBPeripheral, error: NSError?) {
        if (error != null) {
            _connectionState.value = ConnectionState.Failed(
                error.localizedDescription ?: "Service discovery failed"
            )
            return
        }

        // Discover characteristics for each service
        peripheral.services?.forEach { service ->
            (service as? CBService)?.let { cbService ->
                peripheral.discoverCharacteristics(null, cbService)
            }
        }
    }

    internal fun onCharacteristicsDiscovered(peripheral: CBPeripheral, service: CBService, error: NSError?) {
        if (error != null) {
            return
        }

        // Check if this is our read or write service
        val serviceUuid = service.UUID.UUIDString.lowercase()

        if (readServiceUuid != null && serviceUuid == readServiceUuid?.lowercase()) {
            readCharacteristic = service.characteristics?.firstOrNull { char ->
                (char as? CBCharacteristic)?.UUID?.UUIDString?.lowercase() == readCharUuid?.lowercase()
            } as? CBCharacteristic

            // Enable notifications for read characteristic
            readCharacteristic?.let { char ->
                peripheral.setNotifyValue(true, char)
            }
        }

        if (writeServiceUuid != null && serviceUuid == writeServiceUuid?.lowercase()) {
            writeCharacteristic = service.characteristics?.firstOrNull { char ->
                (char as? CBCharacteristic)?.UUID?.UUIDString?.lowercase() == writeCharUuid?.lowercase()
            } as? CBCharacteristic
        }

        // Check if all services have been discovered
        val allServicesDiscovered = peripheral.services?.all { svc ->
            (svc as? CBService)?.characteristics != null
        } ?: false

        if (allServicesDiscovered) {
            // Build complete DiscoveredServices
            val discoveredServices = peripheral.services?.mapNotNull { svc ->
                (svc as? CBService)?.let { cbService ->
                    DiscoveredService(
                        uuid = cbService.UUID.UUIDString,
                        characteristics = cbService.characteristics?.mapNotNull { char ->
                            (char as? CBCharacteristic)?.UUID?.UUIDString
                        } ?: emptyList()
                    )
                }
            } ?: emptyList()

            onServicesDiscoveredCallback?.invoke(
                DiscoveredServices(discoveredServices),
                peripheral.name
            )

            // Connection complete
            val address = peripheral.identifier.UUIDString
            _connectionState.value = ConnectionState.Connected(
                address = address,
                wheelName = peripheral.name ?: "Unknown"
            )

            connectionContinuation?.resume(Result.success(BleConnection(peripheral))) {}
            connectionContinuation = null
        }
    }

    internal fun onCharacteristicValueUpdated(characteristic: CBCharacteristic, error: NSError?) {
        if (error != null) {
            return
        }

        val data = characteristic.value?.toByteArray()
        if (data != null) {
            onDataReceivedCallback?.invoke(data)
        }
    }
}

// ==================== Central Manager Delegate ====================

@OptIn(ExperimentalForeignApi::class)
private class CBCentralManagerDelegateImpl(
    private val manager: BleManager
) : NSObject(), CBCentralManagerDelegateProtocol {

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        manager.onStateUpdated(central.state)
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber
    ) {
        manager.onPeripheralDiscovered(didDiscoverPeripheral, RSSI.intValue)
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        manager.onPeripheralConnected(didConnectPeripheral)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        manager.onConnectionFailed(didFailToConnectPeripheral, error)
    }

    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?
    ) {
        manager.onPeripheralDisconnected(didDisconnectPeripheral, error)
    }
}

// ==================== Peripheral Delegate ====================

@OptIn(ExperimentalForeignApi::class)
private class CBPeripheralDelegateImpl(
    private val manager: BleManager
) : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        manager.onServicesDiscovered(peripheral, didDiscoverServices)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?
    ) {
        manager.onCharacteristicsDiscovered(peripheral, didDiscoverCharacteristicsForService, error)
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?
    ) {
        manager.onCharacteristicValueUpdated(didUpdateValueForCharacteristic, error)
    }
}

/**
 * iOS BLE connection wrapper.
 * Wraps CBPeripheral for type-safe connection handling.
 */
@OptIn(ExperimentalForeignApi::class)
actual class BleConnection(
    private val peripheral: CBPeripheral
) {
    val address: String get() = peripheral.identifier.UUIDString
    val name: String? get() = peripheral.name
    val isConnected: Boolean get() = peripheral.state == CBPeripheralStateConnected
}

// ==================== Extension Functions ====================

/**
 * Convert ByteArray to NSData.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (this.isEmpty()) return NSData()
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}

/**
 * Convert NSData to ByteArray.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)

    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}
