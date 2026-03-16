import Foundation
import FreeWheelCore

/// Manages HW Charger BLE connection independently from the wheel connection.
/// Owns its own BleManager and ChargerConnectionManager.
@MainActor
class ChargerManager: ObservableObject {
    // MARK: - Published State

    @Published private(set) var chargerState: ChargerState = ChargerState.companion.empty()
    @Published private(set) var connectionState: ConnectionStateWrapper = .disconnected

    // MARK: - Scan State

    struct DiscoveredCharger: Identifiable {
        let address: String
        let name: String
        let rssi: Int
        var id: String { address }
    }

    @Published private(set) var isScanning = false
    @Published private(set) var discoveredChargers: [DiscoveredCharger] = []

    // MARK: - Saved Charger Profiles (KMP-backed)

    private let chargerProfileStore = ChargerProfileStore(store: UserDefaultsKeyValueStore(defaults: .standard))

    @Published private(set) var savedAddresses: Set<String> = Set()

    private func refreshSavedAddresses() {
        savedAddresses = chargerProfileStore.getSavedAddresses()
    }

    // MARK: - Private KMP Components

    private var bleManager: BleManager?
    private var chargerConnectionManager: ChargerConnectionManager?

    // MARK: - Flow Observers

    private var chargerStateObserver: FlowObservation?
    private var connectionStateObserver: FlowObservation?

    // MARK: - Initialization

    init() {
        setupKmpComponents()
        refreshSavedAddresses()
    }

    deinit {
        chargerStateObserver?.close()
        connectionStateObserver?.close()
    }

    private func setupKmpComponents() {
        bleManager = BleManager()
        bleManager?.initialize(restoreIdentifier: "FreeWheelChargerBLE")

        guard let ble = bleManager else { return }
        chargerConnectionManager = ChargerConnectionManagerHelper.shared.create(bleManager: ble)

        guard let ccm = chargerConnectionManager else { return }

        // Wire BLE callbacks
        bleManager?.setDataReceivedCallback { [weak self] data in
            guard let ccm = self?.chargerConnectionManager else { return }
            ChargerConnectionManagerHelper.shared.onDataReceived(manager: ccm, data: data)
        }

        bleManager?.setServicesDiscoveredCallback { [weak self] _, _ in
            guard let ccm = self?.chargerConnectionManager else { return }
            ChargerConnectionManagerHelper.shared.onServicesDiscovered(manager: ccm)
        }

        bleManager?.setBleErrorCallback { [weak self] in
            guard let ccm = self?.chargerConnectionManager else { return }
            ChargerConnectionManagerHelper.shared.onBleError(manager: ccm)
        }

        // Start observing state flows
        startObserving(ccm)
    }

    private func startObserving(_ ccm: ChargerConnectionManager) {
        let helper = ChargerConnectionManagerHelper.shared

        chargerStateObserver = helper.observeChargerState(manager: ccm) { [weak self] state in
            Task { @MainActor in
                self?.chargerState = state
            }
        }

        connectionStateObserver = helper.observeConnectionState(manager: ccm) { [weak self] kmpState in
            Task { @MainActor in
                guard let self = self else { return }
                let newState = ConnectionStateWrapper(from: kmpState)
                guard newState != self.connectionState else { return }
                self.connectionState = newState

                // Auto-save profile when connected
                if case .connected(let address, _) = newState {
                    self.autoSaveProfile(address: address)
                }
            }
        }
    }

    // MARK: - Scanning

    func startScan() {
        guard let ble = bleManager else { return }
        isScanning = true
        discoveredChargers = []

        ble.startScanForService(
            serviceUuid: BleUuids.HwCharger.shared.SERVICE,
            onDeviceFound: { [weak self] device in
                Task { @MainActor in
                    self?.onChargerDiscovered(device)
                }
            }
        ) { error in
            if let error = error {
                print("Charger scan error: \(error.localizedDescription)")
            }
        }
    }

    func stopScan() {
        bleManager?.stopScan { [weak self] error in
            Task { @MainActor in
                self?.isScanning = false
                if let error = error {
                    print("Charger stop scan error: \(error.localizedDescription)")
                }
            }
        }
    }

    private func onChargerDiscovered(_ device: BleDevice) {
        let charger = DiscoveredCharger(
            address: device.address,
            name: device.name ?? "HW Charger",
            rssi: Int(device.rssi)
        )
        if let idx = discoveredChargers.firstIndex(where: { $0.address == device.address }) {
            discoveredChargers[idx] = charger
        } else {
            discoveredChargers.append(charger)
        }
        discoveredChargers.sort { $0.rssi > $1.rssi }
    }

    // MARK: - Connection

    func connect(address: String, password: String) {
        stopScan()
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.connect(manager: ccm, address: address, password: password)
    }

    func disconnect() {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.disconnect(manager: ccm)
    }

    // MARK: - Commands

    func setOutputVoltage(_ voltage: Float) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setOutputVoltage(manager: ccm, voltage: voltage)
    }

    func setOutputCurrent(_ current: Float) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setOutputCurrent(manager: ccm, current: current)
    }

    func toggleOutput(_ enable: Bool) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.toggleOutput(manager: ccm, enable: enable)
    }

    func setPowerLimit(_ watts: Int32) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setPowerLimit(manager: ccm, watts: watts)
    }

    func setTwoStageCharging(_ enabled: Bool) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setTwoStageCharging(manager: ccm, enabled: enabled)
    }

    func setEndOfChargeCurrent(_ current: Float) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setEndOfChargeCurrent(manager: ccm, current: current)
    }

    func setPowerOnOutput(_ enabled: Bool) {
        guard let ccm = chargerConnectionManager else { return }
        ChargerConnectionManagerHelper.shared.setPowerOnOutput(manager: ccm, enabled: enabled)
    }

    // MARK: - Profile Management

    func getSavedProfiles() -> [FreeWheelCore.ChargerProfile] {
        chargerProfileStore.getSavedProfiles()
    }

    func saveProfile(_ profile: FreeWheelCore.ChargerProfile) {
        chargerProfileStore.saveProfile(profile: profile)
        refreshSavedAddresses()
    }

    func deleteProfile(address: String) {
        chargerProfileStore.deleteProfile(address: address)
        refreshSavedAddresses()
    }

    private func autoSaveProfile(address: String) {
        let existing = chargerProfileStore.getProfile(address: address)
        saveProfile(FreeWheelCore.ChargerProfile(
            address: address,
            displayName: existing?.displayName ?? "HW Charger",
            password: existing?.password ?? "",
            lastConnectedMs: Int64(Date().timeIntervalSince1970 * 1000)
        ))
    }
}
