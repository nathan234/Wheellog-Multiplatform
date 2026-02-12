package com.cooper.wheellog.core.service

import com.cooper.wheellog.core.ble.BleUuids
import com.cooper.wheellog.core.ble.DiscoveredService
import com.cooper.wheellog.core.ble.DiscoveredServices
import com.cooper.wheellog.core.utils.Lock
import com.cooper.wheellog.core.utils.Logger
import com.cooper.wheellog.core.utils.withLock
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

    // Connection continuation for suspend function (protected by continuationLock)
    private var connectionContinuation: CancellableContinuation<Result<BleConnection>>? = null
    private val continuationLock = Lock()

    // Track which services have completed characteristic discovery
    private val discoveredServiceUuids = mutableSetOf<String>()

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
            centralManager = CBCentralManager(
                delegate = centralDelegate,
                queue = null,
                options = mapOf(CBCentralManagerOptionRestoreIdentifierKey to "WheelLogBLE")
            )
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
            continuationLock.withLock {
                connectionContinuation = continuation
            }

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
                continuationLock.withLock {
                    connectionContinuation?.resume(
                        Result.failure(Exception("Peripheral not found: $address"))
                    ) {}
                    connectionContinuation = null
                }
            }

            continuation.invokeOnCancellation {
                currentPeripheral?.let { central.cancelPeripheralConnection(it) }
                continuationLock.withLock {
                    connectionContinuation = null
                }
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
        discoveredServiceUuids.clear()
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
        // Store peripheral UUID for state restoration
        NSUserDefaults.standardUserDefaults.setObject(
            peripheral.identifier.UUIDString,
            forKey = "WheelLogLastPeripheralUUID"
        )

        _connectionState.value = ConnectionState.DiscoveringServices(
            peripheral.identifier.UUIDString
        )
        // Discover only the services used by supported wheels
        peripheral.discoverServices(serviceUUIDs = wheelServiceUUIDs())
    }

    internal fun onWillRestoreState(peripherals: List<CBPeripheral>?) {
        peripherals?.firstOrNull()?.let { peripheral ->
            currentPeripheral = peripheral
            peripheral.delegate = peripheralDelegate
            Logger.d("BleManager", "Restored peripheral: ${peripheral.identifier.UUIDString}")
        }
    }

    internal fun onConnectionFailed(peripheral: CBPeripheral, error: NSError?) {
        val errorMessage = error?.localizedDescription ?: "Connection failed"
        _connectionState.value = ConnectionState.Failed(errorMessage)

        continuationLock.withLock {
            connectionContinuation?.resume(Result.failure(Exception(errorMessage))) {}
            connectionContinuation = null
        }
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
        discoveredServiceUuids.clear()
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
        val serviceUuid = service.UUID.UUIDString

        if (error != null) {
            Logger.w("BleManager", "Characteristic discovery error for service $serviceUuid: ${error.localizedDescription}")
        }

        // Track this service as having completed characteristic discovery
        discoveredServiceUuids.add(serviceUuid.lowercase())

        // Check if all services have completed characteristic discovery
        val totalServices = peripheral.services?.size ?: 0
        val allServicesDiscovered = discoveredServiceUuids.size >= totalServices

        if (allServicesDiscovered) {
            // Build complete DiscoveredServices (expand short CoreBluetooth UUIDs to 128-bit)
            val discoveredServices = peripheral.services?.mapNotNull { svc ->
                (svc as? CBService)?.let { cbService ->
                    DiscoveredService(
                        uuid = expandCoreBluetoothUuid(cbService.UUID.UUIDString),
                        characteristics = cbService.characteristics?.mapNotNull { char ->
                            (char as? CBCharacteristic)?.let {
                                expandCoreBluetoothUuid(it.UUID.UUIDString)
                            }
                        } ?: emptyList()
                    )
                }
            } ?: emptyList()

            // Invoke callback — this triggers wheel type detection and configureForWheel()
            onServicesDiscoveredCallback?.invoke(
                DiscoveredServices(discoveredServices),
                peripheral.name
            )

            // Now that UUIDs are configured (via callback → Swift → configureForWheel),
            // match and subscribe to the correct characteristics
            setupCharacteristics(peripheral)

            // Connection complete
            val address = peripheral.identifier.UUIDString
            _connectionState.value = ConnectionState.Connected(
                address = address,
                wheelName = peripheral.name ?: "Unknown"
            )

            continuationLock.withLock {
                connectionContinuation?.resume(Result.success(BleConnection(peripheral))) {}
                connectionContinuation = null
            }
        }
    }

    /**
     * Match read/write characteristics against the configured UUIDs and enable notifications.
     * Must be called after configureForWheel() has set the UUID fields.
     */
    private fun setupCharacteristics(peripheral: CBPeripheral) {
        val rServiceUuid = readServiceUuid?.lowercase()
        val rCharUuid = readCharUuid?.lowercase()
        val wServiceUuid = writeServiceUuid?.lowercase()
        val wCharUuid = writeCharUuid?.lowercase()

        if (rServiceUuid == null || rCharUuid == null) {
            Logger.w("BleManager", "Read UUIDs not configured, cannot set up characteristics")
            return
        }

        peripheral.services?.forEach { svc ->
            val cbService = svc as? CBService ?: return@forEach
            val serviceUuid = expandCoreBluetoothUuid(cbService.UUID.UUIDString).lowercase()

            if (serviceUuid == rServiceUuid) {
                readCharacteristic = cbService.characteristics?.firstOrNull { char ->
                    (char as? CBCharacteristic)?.let {
                        expandCoreBluetoothUuid(it.UUID.UUIDString).lowercase() == rCharUuid
                    } ?: false
                } as? CBCharacteristic

                readCharacteristic?.let { char ->
                    peripheral.setNotifyValue(true, char)
                    Logger.d("BleManager", "Subscribed to read characteristic: $rCharUuid")
                }
            }

            if (wServiceUuid != null && wCharUuid != null && serviceUuid == wServiceUuid) {
                writeCharacteristic = cbService.characteristics?.firstOrNull { char ->
                    (char as? CBCharacteristic)?.let {
                        expandCoreBluetoothUuid(it.UUID.UUIDString).lowercase() == wCharUuid
                    } ?: false
                } as? CBCharacteristic

                if (writeCharacteristic != null) {
                    Logger.d("BleManager", "Found write characteristic: $wCharUuid")
                }
            }
        }

        if (readCharacteristic == null) {
            Logger.w("BleManager", "Read characteristic not found: service=$rServiceUuid char=$rCharUuid")
        }
        if (writeCharacteristic == null && wCharUuid != null) {
            Logger.w("BleManager", "Write characteristic not found: service=$wServiceUuid char=$wCharUuid")
        }
    }

    internal fun onCharacteristicValueUpdated(characteristic: CBCharacteristic, error: NSError?) {
        if (error != null) {
            Logger.w("BleManager", "Characteristic update error: ${error.localizedDescription}")
            return
        }

        val data = characteristic.value?.toByteArray()
        if (data != null) {
            Logger.d("BleManager", "Data received: ${data.size} bytes from ${characteristic.UUID.UUIDString}")
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

    override fun centralManager(central: CBCentralManager, willRestoreState: Map<Any?, *>) {
        @Suppress("UNCHECKED_CAST")
        val peripherals = willRestoreState[CBCentralManagerRestoredStatePeripheralsKey] as? List<CBPeripheral>
        manager.onWillRestoreState(peripherals)
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

// ==================== Service UUID Filter ====================

/**
 * Returns the 3 BLE service UUIDs used by all supported wheel types.
 * Passing these to discoverServices() skips standard services (Generic Access,
 * Device Information, Battery, etc.) that add ~50-100ms each to discovery.
 */
private fun wheelServiceUUIDs(): List<CBUUID> = listOf(
    // Kingsong, Gotway/Veteran, Ninebot, Inmotion V1 read
    CBUUID.UUIDWithString(BleUuids.Kingsong.SERVICE),
    // Inmotion V1 write
    CBUUID.UUIDWithString(BleUuids.Inmotion.WRITE_SERVICE),
    // Nordic UART — InmotionV2, NinebotZ
    CBUUID.UUIDWithString(BleUuids.InmotionV2.SERVICE),
)

// ==================== UUID Normalization ====================

/**
 * Expand CoreBluetooth short UUID strings to full 128-bit format.
 *
 * CoreBluetooth returns short UUIDs for standard Bluetooth SIG services:
 * - 4-char: "FFE0" → "0000FFE0-0000-1000-8000-00805F9B34FB"
 * - 8-char: "0000FFE0" → "0000FFE0-0000-1000-8000-00805F9B34FB"
 * - Full 128-bit UUIDs are returned as-is.
 */
private fun expandCoreBluetoothUuid(uuidString: String): String {
    val BLE_BASE_SUFFIX = "-0000-1000-8000-00805F9B34FB"
    return when (uuidString.length) {
        4 -> "0000$uuidString$BLE_BASE_SUFFIX"
        8 -> "$uuidString$BLE_BASE_SUFFIX"
        else -> uuidString
    }
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
