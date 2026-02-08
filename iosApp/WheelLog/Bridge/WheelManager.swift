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

    // MARK: - KMP Components

    private var bleManager: BleManager?
    private var decoderFactory: DefaultWheelDecoderFactory?

    // MARK: - Polling Timers

    private var connectionPollingTimer: Timer?

    // MARK: - Initialization

    nonisolated init() {
        // Setup happens in Task
        Task { @MainActor in
            self.setupKmpComponents()
            self.startPolling()
        }
    }

    deinit {
        connectionPollingTimer?.invalidate()
        connectionPollingTimer = nil
    }

    private func setupKmpComponents() {
        // Initialize KMP BLE manager
        bleManager = BleManager()
        bleManager?.initialize()

        // Initialize decoder factory
        decoderFactory = DefaultWheelDecoderFactory()

        // Set up data received callback
        bleManager?.setDataReceivedCallback { byteArray in
            // Convert KotlinByteArray to Swift Data/ByteArray if needed
            // For now this callback is set up but wheel state would come from WheelConnectionManager
            print("Data received: \(byteArray.size) bytes")
        }

        // Set up services discovered callback
        bleManager?.setServicesDiscoveredCallback { services, deviceName in
            print("Services discovered for device: \(deviceName ?? "unknown")")
            // This would typically trigger wheel type detection
        }
    }

    // MARK: - Polling

    private func startPolling() {
        // Poll connection state at 5Hz
        connectionPollingTimer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollConnectionState()
            }
        }
    }

    private func pollConnectionState() {
        guard let bleManager = bleManager else { return }
        // Access the StateFlow value - KMP exposes it as a property
        if let kmpState = bleManager.connectionState as? ConnectionState {
            connectionState = ConnectionStateWrapper(from: kmpState)
        }

        // Update scanning state from connection state
        if case .scanning = connectionState {
            isScanning = true
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
        guard let bleManager = bleManager else { return }

        connectionState = .connecting(address: address)

        bleManager.connect(address: address) { [weak self] result, error in
            Task { @MainActor in
                if let error = error {
                    self?.connectionState = .failed(error: error.localizedDescription)
                }
                // Connection state will also be updated through polling
            }
        }
    }

    func disconnect() {
        guard let bleManager = bleManager else { return }

        bleManager.disconnect { [weak self] error in
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
