import SwiftUI
import WheelLogCore

struct ScanView: View {
    @EnvironmentObject var wheelManager: WheelManager
    @State private var scanPulse = false

    private var hasDevices: Bool {
        !wheelManager.discoveredDevices.isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("WheelLog")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Spacer()
            }
            .padding()

            // Scan button — always visible
            scanButton
                .onChange(of: wheelManager.isScanning) { scanning in
                    if scanning {
                        scanPulse = true
                    } else {
                        scanPulse = false
                    }
                }

            if hasDevices {
                deviceList
            } else {
                emptyState
            }
        }
        .background(Color(.systemGroupedBackground))
    }

    private var scanButton: some View {
        let isScanning = wheelManager.isScanning
        let size: CGFloat = hasDevices ? 100 : 160

        return Button(action: toggleScan) {
            ZStack {
                Circle()
                    .fill(isScanning ? Color.red : Color.blue)
                    .frame(width: size, height: size)
                    .shadow(color: (isScanning ? Color.red : Color.blue).opacity(0.4), radius: 12, y: 4)

                if isScanning {
                    Circle()
                        .stroke(Color.red.opacity(0.3), lineWidth: 3)
                        .frame(width: size + 20, height: size + 20)
                        .scaleEffect(scanPulse ? 1.3 : 1.0)
                        .opacity(scanPulse ? 0.0 : 0.6)
                        .animation(.easeOut(duration: 1.5).repeatForever(autoreverses: false), value: scanPulse)
                }

                VStack(spacing: hasDevices ? 4 : 8) {
                    Image(systemName: isScanning ? "stop.fill" : (hasDevices ? "arrow.clockwise" : "antenna.radiowaves.left.and.right"))
                        .font(.system(size: hasDevices ? 24 : 40, weight: .medium))
                    Text(isScanning ? "Cancel" : (hasDevices ? "Rescan" : "Scan"))
                        .font(hasDevices ? .body : .title2)
                        .fontWeight(.semibold)
                }
                .foregroundColor(.white)
            }
        }
        .buttonStyle(.plain)
        .padding(.vertical, hasDevices ? 12 : 0)
        .animation(.easeInOut(duration: 0.3), value: hasDevices)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()

            if wheelManager.isScanning {
                Text("Searching for nearby wheels...")
                    .font(.body)
                    .foregroundColor(.secondary)
            } else {
                Text("Tap to search for nearby wheels")
                    .font(.body)
                    .foregroundColor(.secondary)
            }

            #if targetEnvironment(simulator)
            Divider()
                .padding(.vertical, 12)

            VStack(spacing: 12) {
                Text("Simulator Mode")
                    .font(.headline)
                    .foregroundColor(.orange)
                Text("BLE is not available on iOS Simulator")
                    .font(.caption)
                    .foregroundColor(.secondary)

                Button(action: { wheelManager.startMockMode() }) {
                    HStack {
                        Image(systemName: "play.circle.fill")
                        Text("Start Demo Mode")
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.orange)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }

                Button(action: { startKmpTest() }) {
                    HStack {
                        Image(systemName: "testtube.2")
                        Text("Test KMP Decoder")
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }

                Divider()
                    .padding(.vertical, 8)

                Text("Test Wheel Settings Panel")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    testButton("KingSong", wheelType: .kingsong, color: .blue)
                    testButton("Begode", wheelType: .gotway, color: .green)
                    testButton("Veteran", wheelType: .veteran, color: .purple)
                    testButton("Ninebot Z", wheelType: .ninebotZ, color: .orange)
                    testButton("InMotion", wheelType: .inmotion, color: .teal)
                    testButton("InMotion V2", wheelType: .inmotionV2, color: .indigo)
                }
                .padding(.horizontal)
            }
            #endif

            Spacer()
        }
        .frame(maxWidth: .infinity)
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

    #if targetEnvironment(simulator)
    private func testButton(_ label: String, wheelType: WheelType, color: Color) -> some View {
        Button(action: {
            wheelManager.startTestSession(wheelType: wheelType)
        }) {
            Text(label)
                .font(.caption)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(color)
                .foregroundColor(.white)
                .cornerRadius(8)
        }
    }

    private func startKmpTest() {
        // Start a test session with Kingsong wheel type
        wheelManager.startTestSession(wheelType: .kingsong)

        // Inject real KS-S18 packets from KingsongDecoderComparisonTest
        // Expected values after decoding:
        // - Model: KS-S18, Version: 2.05
        // - Speed: 5.15 km/h, Voltage: 65.05V, Current: 2.15A
        // - Temperature: 13°C, Battery: 12%
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            let packets = [
                "aa554b532d5331382d30323035000000bb1484fd",  // Model name
                "aa556919030200009f36d700140500e0a9145a5a",  // Live data
                "aa550000090017011502140100004006b9145a5a",  // Distance/fan
                "aa55000000000000000000000000400cf5145a5a",  // CPU load
                "aa55850c010000000000000016000000f6145a5a"   // Output
            ]
            for packet in packets {
                self.wheelManager.injectTestData(packet)
            }
        }
    }
    #endif
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
