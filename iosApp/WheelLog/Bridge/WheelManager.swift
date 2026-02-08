import Foundation
import Combine
import WheelLogCore

/// Swift wrapper for KMP wheel management APIs.
/// Provides an ObservableObject interface for SwiftUI integration.
@MainActor
class WheelManager: ObservableObject {
    // MARK: - Published State

    @Published private(set) var wheelState: WheelStateWrapper = WheelStateWrapper()
    @Published private(set) var connectionState: ConnectionStateWrapper = .disconnected
    @Published private(set) var discoveredDevices: [DiscoveredDevice] = []
    @Published private(set) var isScanning: Bool = false
    @Published var isMockMode: Bool = false
    @Published var isTestMode: Bool = false

    // MARK: - KMP Components

    private var bleManager: BleManager?
    private var connectionManager: WheelConnectionManager?

    // MARK: - Mock Data Provider

    private let mockDataProvider = MockDataProvider()

    // MARK: - Polling Timers

    private var statePollingTimer: Timer?

    // MARK: - Initialization

    nonisolated init() {
        // Setup happens in Task
        Task { @MainActor in
            self.setupKmpComponents()
            self.setupMockProvider()
            self.startPolling()

            // Auto-enable mock mode on simulator
            #if targetEnvironment(simulator)
            self.isMockMode = true
            #endif
        }
    }

    deinit {
        statePollingTimer?.invalidate()
        statePollingTimer = nil
        mockDataProvider.stop()
    }

    private func setupKmpComponents() {
        // Initialize KMP BLE manager
        bleManager = BleManager()
        bleManager?.initialize()

        // Create WheelConnectionManager using iOS factory
        guard let ble = bleManager else { return }
        connectionManager = WheelConnectionManagerFactory.shared.create(bleManager: ble)

        // Wire BLE data to connection manager
        bleManager?.setDataReceivedCallback { [weak self] data in
            self?.connectionManager?.onDataReceived(data: data)
        }

        // Wire service discovery to connection manager
        bleManager?.setServicesDiscoveredCallback { [weak self] services, deviceName in
            self?.connectionManager?.onServicesDiscovered(services: services, deviceName: deviceName)
        }
    }

    private func setupMockProvider() {
        mockDataProvider.onStateUpdate = { [weak self] state in
            Task { @MainActor in
                self?.updateFromMock(state)
            }
        }
    }

    private func updateFromMock(_ state: WheelStateBridge) {
        wheelState = WheelStateWrapper(
            speedKmh: state.speed,
            voltage: state.voltage,
            current: state.current,
            power: state.power,
            temperature: Int(state.temperature),
            batteryLevel: Int(state.battery),
            totalDistanceKm: state.totalDistance,
            wheelDistanceKm: state.tripDistance,
            pwmPercent: (state.speed / 50.0) * 100.0,  // Estimated PWM
            wheelType: "MOCK",
            name: state.name,
            model: state.model
        )

        if state.isConnected {
            connectionState = .connected(address: "MOCK-DEVICE", wheelName: state.model)
        }
    }

    // MARK: - Mock Mode

    func startMockMode() {
        isMockMode = true
        connectionState = .connecting(address: "MOCK-DEVICE")

        // Brief delay to simulate connection
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            self?.mockDataProvider.start()
        }
    }

    func stopMockMode() {
        mockDataProvider.stop()
        isMockMode = false
        connectionState = .disconnected
        wheelState = WheelStateWrapper()
    }

    // MARK: - Test Data Injection

    /// Inject raw BLE packet for testing the KMP decoder pipeline.
    /// Use this to test with recorded wheel data on simulator.
    func injectTestData(_ hexString: String) {
        guard let cm = connectionManager else {
            print("Connection manager not initialized")
            return
        }

        // Convert hex string to KotlinByteArray
        let bytes = hexStringToBytes(hexString)
        let kotlinBytes = KotlinByteArray(size: Int32(bytes.count))
        for (index, byte) in bytes.enumerated() {
            kotlinBytes.set(index: Int32(index), value: byte)
        }

        cm.onDataReceived(data: kotlinBytes)
    }

    /// Start a test session with a specific wheel type.
    /// This simulates connecting without actual BLE.
    func startTestSession(wheelType: WheelType) {
        isTestMode = true
        connectionManager?.onWheelTypeDetected(wheelType: wheelType)
        connectionState = .connected(address: "TEST-DEVICE", wheelName: wheelType.name)
    }

    func stopTestMode() {
        isTestMode = false
        connectionState = .disconnected
        wheelState = WheelStateWrapper()
    }

    private func hexStringToBytes(_ hex: String) -> [Int8] {
        let cleanHex = hex.replacingOccurrences(of: " ", with: "")
        var bytes: [Int8] = []
        var index = cleanHex.startIndex
        while index < cleanHex.endIndex {
            let nextIndex = cleanHex.index(index, offsetBy: 2)
            if let byte = UInt8(cleanHex[index..<nextIndex], radix: 16) {
                bytes.append(Int8(bitPattern: byte))
            }
            index = nextIndex
        }
        return bytes
    }

    // MARK: - Polling

    private func startPolling() {
        // Poll wheel state and connection state at 10Hz
        statePollingTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollState()
            }
        }
    }

    private func pollState() {
        guard !isMockMode else { return }  // Skip polling in mock mode
        guard let cm = connectionManager else { return }

        // In test mode, only poll wheel state (not connection state)
        if isTestMode {
            let kmpWheelState = WheelConnectionManagerFactory.shared.getWheelState(manager: cm)
            let newWheelState = WheelStateWrapper(from: kmpWheelState)
            if newWheelState != wheelState {
                wheelState = newWheelState
            }
            return
        }

        // Poll connection state using iOS helper
        let kmpConnectionState = WheelConnectionManagerFactory.shared.getConnectionState(manager: cm)
        let newState = ConnectionStateWrapper(from: kmpConnectionState)
        if newState != connectionState {
            connectionState = newState
        }

        // Poll wheel state using iOS helper
        let kmpWheelState = WheelConnectionManagerFactory.shared.getWheelState(manager: cm)
        let newWheelState = WheelStateWrapper(from: kmpWheelState)
        if newWheelState != wheelState {
            wheelState = newWheelState
        }

        // Update scanning state
        if case .scanning = connectionState {
            isScanning = true
        } else if isScanning && connectionState != .scanning {
            isScanning = false
        }
    }

    // MARK: - Scanning

    func startScan() {
        guard let bleManager = bleManager else { return }

        isScanning = true
        discoveredDevices = []

        bleManager.startScan(onDeviceFound: { [weak self] device in
            Task { @MainActor in
                self?.onDeviceDiscovered(device)
            }
        }) { error in
            if let error = error {
                print("Scan error: \(error.localizedDescription)")
            }
        }
    }

    func stopScan() {
        guard let bleManager = bleManager else { return }

        bleManager.stopScan { [weak self] error in
            Task { @MainActor in
                self?.isScanning = false
                if let error = error {
                    print("Stop scan error: \(error.localizedDescription)")
                }
            }
        }
    }

    private func onDeviceDiscovered(_ device: BleDevice) {
        let discovered = DiscoveredDevice(
            address: device.address,
            name: device.name ?? "Unknown",
            rssi: Int(device.rssi)
        )

        // Update or add device
        if let index = discoveredDevices.firstIndex(where: { $0.address == discovered.address }) {
            discoveredDevices[index] = discovered
        } else {
            discoveredDevices.append(discovered)
        }

        // Sort by RSSI (strongest first)
        discoveredDevices.sort { $0.rssi > $1.rssi }
    }

    // MARK: - Connection

    func connect(address: String) {
        guard let connectionManager = connectionManager else { return }

        connectionState = .connecting(address: address)

        // Use WheelConnectionManager for connection
        connectionManager.connect(address: address, wheelType: nil) { error in
            if let error = error {
                Task { @MainActor in
                    self.connectionState = .failed(error: error.localizedDescription)
                }
            }
            // Connection state will be updated through polling
        }
    }

    func disconnect() {
        guard let connectionManager = connectionManager else { return }

        connectionManager.disconnect { [weak self] error in
            Task { @MainActor in
                self?.connectionState = .disconnected
                self?.wheelState = WheelStateWrapper()
                if let error = error {
                    print("Disconnect error: \(error.localizedDescription)")
                }
            }
        }
    }
}

// MARK: - Swift Wrappers for KMP Types

/// Swift wrapper for KMP WheelState
struct WheelStateWrapper: Equatable {
    // Core telemetry
    let speedKmh: Double
    let voltage: Double
    let current: Double
    let power: Double
    let temperature: Int
    let batteryLevel: Int

    // Distance
    let totalDistanceKm: Double
    let wheelDistanceKm: Double

    // PWM
    let pwmPercent: Double

    // Wheel info
    let wheelType: String
    let name: String
    let model: String

    init() {
        speedKmh = 0
        voltage = 0
        current = 0
        power = 0
        temperature = 0
        batteryLevel = 0
        totalDistanceKm = 0
        wheelDistanceKm = 0
        pwmPercent = 0
        wheelType = "Unknown"
        name = ""
        model = ""
    }

    init(
        speedKmh: Double,
        voltage: Double,
        current: Double,
        power: Double,
        temperature: Int,
        batteryLevel: Int,
        totalDistanceKm: Double,
        wheelDistanceKm: Double,
        pwmPercent: Double,
        wheelType: String,
        name: String,
        model: String
    ) {
        self.speedKmh = speedKmh
        self.voltage = voltage
        self.current = current
        self.power = power
        self.temperature = temperature
        self.batteryLevel = batteryLevel
        self.totalDistanceKm = totalDistanceKm
        self.wheelDistanceKm = wheelDistanceKm
        self.pwmPercent = pwmPercent
        self.wheelType = wheelType
        self.name = name
        self.model = model
    }

    init(from kmpState: WheelState) {
        speedKmh = kmpState.speedKmh
        voltage = kmpState.voltageV
        current = kmpState.currentA
        power = kmpState.powerW
        temperature = Int(kmpState.temperatureC)
        batteryLevel = Int(kmpState.batteryLevel)
        totalDistanceKm = kmpState.totalDistanceKm
        wheelDistanceKm = kmpState.wheelDistanceKm
        pwmPercent = kmpState.pwmPercent
        wheelType = kmpState.wheelType.name
        name = kmpState.name
        model = kmpState.model
    }
}

/// Swift wrapper for KMP ConnectionState
enum ConnectionStateWrapper: Equatable {
    case disconnected
    case scanning
    case connecting(address: String)
    case discoveringServices(address: String)
    case connected(address: String, wheelName: String)
    case connectionLost(address: String, reason: String)
    case failed(error: String)

    var isConnected: Bool {
        if case .connected = self { return true }
        return false
    }

    var isConnecting: Bool {
        switch self {
        case .connecting, .discoveringServices:
            return true
        default:
            return false
        }
    }

    var isDisconnected: Bool {
        switch self {
        case .disconnected, .failed, .connectionLost:
            return true
        default:
            return false
        }
    }

    var statusText: String {
        switch self {
        case .disconnected:
            return "Disconnected"
        case .scanning:
            return "Scanning..."
        case .connecting:
            return "Connecting..."
        case .discoveringServices:
            return "Discovering services..."
        case .connected(_, let name):
            return "Connected to \(name)"
        case .connectionLost(_, let reason):
            return "Connection lost: \(reason)"
        case .failed(let error):
            return "Failed: \(error)"
        }
    }

    init(from kmpState: ConnectionState) {
        switch kmpState {
        case is ConnectionState.Disconnected:
            self = .disconnected
        case is ConnectionState.Scanning:
            self = .scanning
        case let connecting as ConnectionState.Connecting:
            self = .connecting(address: connecting.address)
        case let discovering as ConnectionState.DiscoveringServices:
            self = .discoveringServices(address: discovering.address)
        case let connected as ConnectionState.Connected:
            self = .connected(address: connected.address, wheelName: connected.wheelName)
        case let lost as ConnectionState.ConnectionLost:
            self = .connectionLost(address: lost.address, reason: lost.reason)
        case let failed as ConnectionState.Failed:
            self = .failed(error: failed.error)
        default:
            self = .disconnected
        }
    }
}

/// Swift representation of a discovered BLE device
struct DiscoveredDevice: Identifiable, Equatable {
    let id: String
    let address: String
    let name: String
    let rssi: Int

    init(address: String, name: String, rssi: Int) {
        self.id = address
        self.address = address
        self.name = name
        self.rssi = rssi
    }

    var rssiDescription: String {
        if rssi >= -50 { return "Excellent" }
        if rssi >= -60 { return "Good" }
        if rssi >= -70 { return "Fair" }
        return "Weak"
    }
}

// MARK: - Preview Support

#if DEBUG
extension WheelManager {
    /// Create a preview instance with mock data
    static func preview(
        connectionState: ConnectionStateWrapper = .disconnected,
        wheelState: WheelStateWrapper = WheelStateWrapper(),
        devices: [DiscoveredDevice] = []
    ) -> WheelManager {
        let manager = WheelManager()
        // Note: Would need to expose setters for preview, this is a placeholder
        return manager
    }
}
#endif
