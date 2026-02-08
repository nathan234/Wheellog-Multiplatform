import SwiftUI

struct ScanView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("WheelLog")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Spacer()
                scanButton
            }
            .padding()

            // Device list
            if wheelManager.discoveredDevices.isEmpty {
                emptyState
            } else {
                deviceList
            }
        }
        .background(Color(.systemGroupedBackground))
    }

    private var scanButton: some View {
        Button(action: toggleScan) {
            HStack(spacing: 6) {
                if wheelManager.isScanning {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                        .scaleEffect(0.8)
                }
                Text(wheelManager.isScanning ? "Stop" : "Scan")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(wheelManager.isScanning ? Color.red : Color.blue)
            .foregroundColor(.white)
            .cornerRadius(8)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            Text("No Devices Found")
                .font(.title2)
                .fontWeight(.medium)
            Text("Tap Scan to search for nearby wheels")
                .font(.body)
                .foregroundColor(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .background(Color(.systemGroupedBackground))
    }

    private var deviceList: some View {
        List {
            Section {
                ForEach(wheelManager.discoveredDevices) { device in
                    DeviceRow(device: device)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            connectToDevice(device)
                        }
                }
            } header: {
                Text("Available Devices")
            } footer: {
                if wheelManager.isScanning {
                    Text("Scanning for devices...")
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func toggleScan() {
        if wheelManager.isScanning {
            wheelManager.stopScan()
        } else {
            wheelManager.startScan()
        }
    }

    private func connectToDevice(_ device: DiscoveredDevice) {
        wheelManager.stopScan()
        wheelManager.connect(address: device.address)
    }
}

struct DeviceRow: View {
    let device: DiscoveredDevice

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(device.name)
                    .font(.body)
                    .fontWeight(.medium)
                Text(device.address)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                signalStrengthIcon
                Text(device.rssiDescription)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var signalStrengthIcon: some View {
        HStack(spacing: 2) {
            ForEach(0..<4) { index in
                RoundedRectangle(cornerRadius: 1)
                    .fill(barColor(for: index))
                    .frame(width: 4, height: CGFloat(6 + index * 3))
            }
        }
    }

    private func barColor(for index: Int) -> Color {
        let bars = signalBars
        return index < bars ? .green : .gray.opacity(0.3)
    }

    private var signalBars: Int {
        if device.rssi >= -50 { return 4 }
        if device.rssi >= -60 { return 3 }
        if device.rssi >= -70 { return 2 }
        return 1
    }
}

#Preview {
    ScanView()
        .environmentObject(WheelManager())
}
