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

    // Unit preferences (persisted to UserDefaults, matching Android's use_mph / use_fahrenheit)
    @Published var useMph: Bool = UserDefaults.standard.bool(forKey: "use_mph") {
        didSet { UserDefaults.standard.set(useMph, forKey: "use_mph") }
    }
    @Published var useFahrenheit: Bool = UserDefaults.standard.bool(forKey: "use_fahrenheit") {
        didSet { UserDefaults.standard.set(useFahrenheit, forKey: "use_fahrenheit") }
    }
    @Published var isLightOn: Bool = false

    // Alarm settings (persisted to UserDefaults, stored in km/h and °C internally)
    @Published var alarmsEnabled: Bool = UserDefaults.standard.bool(forKey: "alarms_enabled") {
        didSet { UserDefaults.standard.set(alarmsEnabled, forKey: "alarms_enabled") }
    }
    @Published var alarm1Speed: Double = UserDefaults.standard.double(forKey: "alarm_1_speed") {
        didSet { UserDefaults.standard.set(alarm1Speed, forKey: "alarm_1_speed") }
    }
    @Published var alarm2Speed: Double = UserDefaults.standard.double(forKey: "alarm_2_speed") {
        didSet { UserDefaults.standard.set(alarm2Speed, forKey: "alarm_2_speed") }
    }
    @Published var alarm3Speed: Double = UserDefaults.standard.double(forKey: "alarm_3_speed") {
        didSet { UserDefaults.standard.set(alarm3Speed, forKey: "alarm_3_speed") }
    }
    @Published var alarmCurrent: Double = UserDefaults.standard.double(forKey: "alarm_current") {
        didSet { UserDefaults.standard.set(alarmCurrent, forKey: "alarm_current") }
    }
    @Published var alarmTemperature: Double = UserDefaults.standard.double(forKey: "alarm_temperature") {
        didSet { UserDefaults.standard.set(alarmTemperature, forKey: "alarm_temperature") }
    }
    @Published var alarmBattery: Double = UserDefaults.standard.double(forKey: "alarm_battery") {
        didSet { UserDefaults.standard.set(alarmBattery, forKey: "alarm_battery") }
    }

    // Alarm action (Feature 1)
    @Published var alarmAction: AlarmAction = AlarmAction(rawValue: UserDefaults.standard.integer(forKey: "alarm_action")) ?? .phoneOnly {
        didSet { UserDefaults.standard.set(alarmAction.rawValue, forKey: "alarm_action") }
    }
    @Published private(set) var activeAlarms: Set<AlarmType> = []

    // Connection settings (persisted to UserDefaults)
    @Published var autoReconnect: Bool = UserDefaults.standard.bool(forKey: "use_reconnect") {
        didSet { UserDefaults.standard.set(autoReconnect, forKey: "use_reconnect") }
    }
    @Published var showUnknownDevices: Bool = UserDefaults.standard.bool(forKey: "show_unknown_devices") {
        didSet { UserDefaults.standard.set(showUnknownDevices, forKey: "show_unknown_devices") }
    }

    // Auto-reconnect state (Feature 2)
    @Published private(set) var reconnectState: AutoReconnectManager.ReconnectState = .idle

    // Logging settings (Feature 3)
    @Published var autoStartLogging: Bool = UserDefaults.standard.bool(forKey: "auto_start_logging") {
        didSet { UserDefaults.standard.set(autoStartLogging, forKey: "auto_start_logging") }
    }
    @Published var logGPS: Bool = UserDefaults.standard.bool(forKey: "log_gps") {
        didSet { UserDefaults.standard.set(logGPS, forKey: "log_gps") }
    }
    @Published private(set) var isLogging: Bool = false

    // MARK: - KMP Components

    private var bleManager: BleManager?
    private var connectionManager: WheelConnectionManager?

    // MARK: - Demo Data Provider (KMP)

    private let demoProvider = WheelConnectionManagerFactory.shared.createDemoProvider()

    // MARK: - Feature Managers

    let alarmManager = AlarmManager()
    let reconnectManager = AutoReconnectManager()
    let rideLogger = RideLogger()
    let rideStore = RideStore()
    let locationManager = LocationManager()
    let backgroundManager = BackgroundManager()
    let telemetryBuffer = TelemetryBuffer()

    // MARK: - Connection Tracking

    private var lastConnectedAddress: String?
    private var previousConnectionState: ConnectionStateWrapper = .disconnected

    // MARK: - Polling Timers

    private var statePollingTimer: Timer?
    private var demoPollingTimer: Timer?

    // MARK: - Initialization

    nonisolated init() {
        // Setup happens in Task
        Task { @MainActor in
            self.setupKmpComponents()
            self.setupAlarmCallbacks()
            self.startPolling()
            self.backgroundManager.requestNotificationPermission()

            // Auto-enable mock mode on simulator
            #if targetEnvironment(simulator)
            self.isMockMode = true
            #endif
        }
    }

    deinit {
        statePollingTimer?.invalidate()
        statePollingTimer = nil
        demoPollingTimer?.invalidate()
        demoPollingTimer = nil
        WheelConnectionManagerFactory.shared.stopDemo(provider: demoProvider)
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
            guard let self = self else { return }
            self.connectionManager?.onServicesDiscovered(services: services, deviceName: deviceName)

            // After wheel type detection, configure BLE manager with the detected UUIDs
            // so it can match and subscribe to the correct characteristics
            if let info = self.connectionManager?.getConnectionInfo() {
                self.bleManager?.configureForWheel(
                    readServiceUuid: info.readServiceUuid,
                    readCharUuid: info.readCharacteristicUuid,
                    writeServiceUuid: info.writeServiceUuid,
                    writeCharUuid: info.writeCharacteristicUuid
                )
            }
        }
    }

    private func setupAlarmCallbacks() {
        alarmManager.sendWheelBeep = { [weak self] in
            self?.wheelBeep()
        }

        alarmManager.onAlarmFired = { [weak self] type, message in
            guard let self = self else { return }
            if self.backgroundManager.isInBackground {
                self.backgroundManager.postAlarmNotification(type: type, value: message)
            }
        }
    }

    // MARK: - Mock Mode

    func startMockMode() {
        isMockMode = true
        connectionState = .connecting(address: "DEMO-DEVICE")

        // Brief delay to simulate connection
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
            guard let self = self else { return }
            WheelConnectionManagerFactory.shared.startDemo(provider: self.demoProvider)
            self.connectionState = .connected(address: "DEMO-DEVICE", wheelName: "Demo Wheel")
            self.startDemoPolling()
        }
    }

    func stopMockMode() {
        demoPollingTimer?.invalidate()
        demoPollingTimer = nil
        WheelConnectionManagerFactory.shared.stopDemo(provider: demoProvider)
        isMockMode = false
        connectionState = .disconnected
        wheelState = WheelStateWrapper()
        telemetryBuffer.clear()
    }

    private func startDemoPolling() {
        demoPollingTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollDemoState()
            }
        }
    }

    private func pollDemoState() {
        let kmpState = WheelConnectionManagerFactory.shared.getDemoState(provider: demoProvider)
        let newWheelState = WheelStateWrapper(from: kmpState)
        guard newWheelState != wheelState else { return }

        wheelState = newWheelState

        // Feed telemetry buffer for chart view
        telemetryBuffer.addSampleIfNeeded(
            speedKmh: wheelState.speedKmh,
            voltage: wheelState.voltage,
            current: wheelState.current,
            power: wheelState.power,
            temperature: wheelState.temperature,
            battery: wheelState.batteryLevel
        )

        // Check alarms
        let values = AlarmManager.WheelValues(
            speedKmh: wheelState.speedKmh,
            current: wheelState.current,
            temperature: wheelState.temperature,
            batteryLevel: wheelState.batteryLevel
        )
        let settings = AlarmManager.AlarmSettings(
            enabled: alarmsEnabled,
            alarm1Speed: alarm1Speed,
            alarm2Speed: alarm2Speed,
            alarm3Speed: alarm3Speed,
            alarmCurrent: alarmCurrent,
            alarmTemperature: alarmTemperature,
            alarmBattery: alarmBattery,
            action: alarmAction
        )
        alarmManager.checkAlarms(values: values, settings: settings)
        activeAlarms = alarmManager.activeAlarms
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
        telemetryBuffer.clear()
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
        // Poll wheel state and connection state at 20Hz
        statePollingTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.pollState()
            }
        }
    }

    private func pollState() {
        guard !isMockMode else { return }  // Demo mode uses its own polling timer
        guard let cm = connectionManager else { return }

        // In test mode, only poll wheel state (not connection state)
        if isTestMode {
            let kmpWheelState = WheelConnectionManagerFactory.shared.getWheelState(manager: cm)
            let newWheelState = WheelStateWrapper(from: kmpWheelState)
            if newWheelState != wheelState {
                syncUnitsFromWheel(newWheelState)
                wheelState = newWheelState
            }
            return
        }

        // Poll connection state using iOS helper
        let kmpConnectionState = WheelConnectionManagerFactory.shared.getConnectionState(manager: cm)
        let newState = ConnectionStateWrapper(from: kmpConnectionState)
        if newState != connectionState {
            handleConnectionStateChange(from: connectionState, to: newState)
            connectionState = newState
        }

        // Poll wheel state using iOS helper
        let kmpWheelState = WheelConnectionManagerFactory.shared.getWheelState(manager: cm)
        let newWheelState = WheelStateWrapper(from: kmpWheelState)
        if newWheelState != wheelState {
            syncUnitsFromWheel(newWheelState)
            wheelState = newWheelState
        }

        // Update scanning state
        if case .scanning = connectionState {
            isScanning = true
        } else if isScanning && connectionState != .scanning {
            isScanning = false
        }

        // Feature 1: Check alarms when connected
        if connectionState.isConnected {
            let values = AlarmManager.WheelValues(
                speedKmh: wheelState.speedKmh,
                current: wheelState.current,
                temperature: wheelState.temperature,
                batteryLevel: wheelState.batteryLevel
            )
            let settings = AlarmManager.AlarmSettings(
                enabled: alarmsEnabled,
                alarm1Speed: alarm1Speed,
                alarm2Speed: alarm2Speed,
                alarm3Speed: alarm3Speed,
                alarmCurrent: alarmCurrent,
                alarmTemperature: alarmTemperature,
                alarmBattery: alarmBattery,
                action: alarmAction
            )
            alarmManager.checkAlarms(values: values, settings: settings)
            activeAlarms = alarmManager.activeAlarms
        }

        // Feature 3: Write ride log sample
        if isLogging {
            let sampleData = RideLogger.SampleData(
                speedKmh: wheelState.speedKmh,
                voltage: wheelState.voltage,
                current: wheelState.current,
                power: wheelState.power,
                pwm: wheelState.pwmPercent,
                batteryLevel: wheelState.batteryLevel,
                wheelDistanceKm: wheelState.wheelDistanceKm,
                totalDistanceKm: wheelState.totalDistanceKm,
                temperature: wheelState.temperature
            )
            rideLogger.writeSampleIfThrottled(
                data: sampleData,
                location: locationManager.currentLocation,
                includeGPS: logGPS
            )
        }

        // Feature 6: Telemetry buffer sampling
        if connectionState.isConnected {
            telemetryBuffer.addSampleIfNeeded(
                speedKmh: wheelState.speedKmh,
                voltage: wheelState.voltage,
                current: wheelState.current,
                power: wheelState.power,
                temperature: wheelState.temperature,
                battery: wheelState.batteryLevel
            )
        }

        // Feature 2: Update reconnect state
        reconnectState = reconnectManager.state
    }

    // MARK: - Connection State Changes

    private func handleConnectionStateChange(from oldState: ConnectionStateWrapper, to newState: ConnectionStateWrapper) {
        // Track connected address
        if case .connected(let address, _) = newState {
            lastConnectedAddress = address
            reconnectManager.onConnectionEstablished()

            // Auto-start logging if enabled
            if autoStartLogging && !isLogging {
                startLogging()
            }
        }

        // Detect connection lost → start auto-reconnect
        if case .connectionLost(let address, _) = newState {
            if autoReconnect {
                reconnectManager.startReconnecting(address: address) { [weak self] addr in
                    guard let self = self else { return }
                    self.connect(address: addr)
                }
            }

            // Stop logging on disconnect
            if isLogging {
                stopLogging()
            }

            telemetryBuffer.clear()
        }

        // Also handle explicit disconnected state
        if case .disconnected = newState, oldState.isConnected {
            if isLogging {
                stopLogging()
            }
            telemetryBuffer.clear()
        }
    }

    // MARK: - Unit Sync

    /// Auto-set useMph based on the wheel's reported miles setting.
    /// Called when wheel state changes so the app matches the wheel's configuration.
    private func syncUnitsFromWheel(_ newState: WheelStateWrapper) {
        if newState.inMiles != wheelState.inMiles {
            useMph = newState.inMiles
        }
    }

    // MARK: - Wheel Commands

    func wheelBeep() {
        guard let cm = connectionManager else { return }
        WheelConnectionManagerFactory.shared.sendBeep(manager: cm)
    }

    func toggleLight() {
        guard let cm = connectionManager else { return }
        isLightOn.toggle()
        WheelConnectionManagerFactory.shared.sendToggleLight(manager: cm, enabled: isLightOn)
    }

    func setPedalsMode(_ mode: Int) {
        guard let cm = connectionManager else { return }
        WheelConnectionManagerFactory.shared.sendSetPedalsMode(manager: cm, mode: Int32(mode))
    }

    // MARK: - Ride Logging (Feature 3)

    func startLogging() {
        if logGPS {
            locationManager.startTracking()
        }
        if rideLogger.startLogging(includeGPS: logGPS) {
            isLogging = true
        }
    }

    func stopLogging() {
        if let metadata = rideLogger.stopLogging(currentDistance: wheelState.totalDistanceKm) {
            rideStore.addRide(metadata)
        }
        locationManager.stopTracking()
        isLogging = false
    }

    // MARK: - Background Mode (Feature 4)

    func onEnterBackground() {
        if connectionState.isConnected {
            backgroundManager.beginBackgroundTask()
        }
    }

    func onEnterForeground() {
        backgroundManager.endBackgroundTask()
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

        // Explicit disconnect — stop reconnection
        reconnectManager.stop()

        // Stop logging
        if isLogging {
            stopLogging()
        }

        connectionManager.disconnect { [weak self] error in
            Task { @MainActor in
                self?.connectionState = .disconnected
                self?.wheelState = WheelStateWrapper()
                self?.telemetryBuffer.clear()
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

    // Wheel-reported settings
    let inMiles: Bool

    // Wheel settings (from BLE frame 0x04)
    let pedalsMode: Int32    // 0=Hard, 1=Medium, 2=Soft, -1=unknown
    let tiltBackSpeed: Int32 // km/h
    let lightMode: Int32     // 0=Off, 1=On, 2=Strobe, -1=unknown
    let ledMode: Int32       // 0-9, -1=unknown

    // BMS data
    let bms1: BmsSnapshotWrapper?
    let bms2: BmsSnapshotWrapper?

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
        inMiles = false
        pedalsMode = -1
        tiltBackSpeed = 0
        lightMode = -1
        ledMode = -1
        bms1 = nil
        bms2 = nil
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
        model: String,
        inMiles: Bool = false,
        pedalsMode: Int32 = -1,
        tiltBackSpeed: Int32 = 0,
        lightMode: Int32 = -1,
        ledMode: Int32 = -1,
        bms1: BmsSnapshotWrapper? = nil,
        bms2: BmsSnapshotWrapper? = nil
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
        self.inMiles = inMiles
        self.pedalsMode = pedalsMode
        self.tiltBackSpeed = tiltBackSpeed
        self.lightMode = lightMode
        self.ledMode = ledMode
        self.bms1 = bms1
        self.bms2 = bms2
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
        inMiles = kmpState.inMiles
        pedalsMode = kmpState.pedalsMode
        tiltBackSpeed = kmpState.tiltBackSpeed
        lightMode = kmpState.lightMode
        ledMode = kmpState.ledMode
        bms1 = kmpState.bms1.map { BmsSnapshotWrapper(from: $0) }
        bms2 = kmpState.bms2.map { BmsSnapshotWrapper(from: $0) }
    }
}

/// Swift wrapper for KMP BmsSnapshot
struct BmsSnapshotWrapper: Equatable {
    let serialNumber: String
    let versionNumber: String
    let factoryCap: Int
    let actualCap: Int
    let fullCycles: Int
    let chargeCount: Int
    let mfgDateStr: String
    let status: Int
    let remCap: Int
    let remPerc: Int
    let current: Double
    let voltage: Double
    let temp1: Double
    let temp2: Double
    let health: Int
    let minCell: Double
    let maxCell: Double
    let cellDiff: Double
    let avgCell: Double
    let minCellNum: Int
    let maxCellNum: Int
    let cellNum: Int
    let cells: [Double]

    init(from snapshot: BmsSnapshot) {
        serialNumber = snapshot.serialNumber
        versionNumber = snapshot.versionNumber
        factoryCap = Int(snapshot.factoryCap)
        actualCap = Int(snapshot.actualCap)
        fullCycles = Int(snapshot.fullCycles)
        chargeCount = Int(snapshot.chargeCount)
        mfgDateStr = snapshot.mfgDateStr
        status = Int(snapshot.status)
        remCap = Int(snapshot.remCap)
        remPerc = Int(snapshot.remPerc)
        current = snapshot.current
        voltage = snapshot.voltage
        temp1 = snapshot.temp1
        temp2 = snapshot.temp2
        health = Int(snapshot.health)
        minCell = snapshot.minCell
        maxCell = snapshot.maxCell
        cellDiff = snapshot.cellDiff
        avgCell = snapshot.avgCell
        minCellNum = Int(snapshot.minCellNum)
        maxCellNum = Int(snapshot.maxCellNum)
        cellNum = Int(snapshot.cellNum)
        cells = (0..<Int(snapshot.cellNum)).map { snapshot.cells[Int32($0)] as! Double }
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
