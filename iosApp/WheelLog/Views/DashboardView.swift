import SwiftUI
import WheelLogCore

// CROSS-PLATFORM SYNC: This view mirrors app/.../compose/screens/DashboardScreen.kt.
// When adding, removing, or reordering sections, update the counterpart.
//
// Shared sections (in order):
//  1. Alarm banner
//  2. Speed gauge (tappable → metric detail)
//  3. 2x3 Gauge Tile Grid: Speed, Battery, Power, PWM, Temp, GPS Speed
//  4. Stats: Voltage, Current, Trip Distance, Total Distance
//  5. Wheel settings (conditional on pedalsMode >= 0; tappable → WheelSettingsView)
//  6. Wheel info: Name, Model, Type, Firmware
//  7. Demo/Test mode badge (iOS has both Demo + Test badges)
//  8. Controls: Horn, Light (Android also has Settings button)
//  9. Record/BMS/Chart row (button order differs: Android=Record,Chart,BMS; iOS=Record,BMS,Chart)
// 10. Disconnect button

struct DashboardView: View {
    @EnvironmentObject var wheelManager: WheelManager
    @State private var showChart = false
    @State private var showBms = false
    @State private var selectedMetric: String?

    private var displaySpeed: Double {
        DisplayUtils.shared.convertSpeed(kmh: wheelManager.wheelState.speedKmh, useMph: wheelManager.useMph)
    }

    private var speedUnit: String {
        DisplayUtils.shared.speedUnit(useMph: wheelManager.useMph)
    }

    private var maxSpeed: Double {
        DisplayUtils.shared.maxSpeedDefault(useMph: wheelManager.useMph)
    }

    // MARK: - Tile Helpers

    private func tileColor(metric: MetricType, value: Double) -> Color {
        let effectiveMax = wheelManager.telemetryBuffer.buffer.effectiveMax(metric: metric)
        let progress = effectiveMax > 0 ? value / effectiveMax : 0
        let zone = metric.colorZone(progress: progress)
        switch zone {
        case .green: return .green
        case .orange: return .orange
        case .red: return .red
        default: return .gray
        }
    }

    private func sparkline(metric: MetricType) -> [Double] {
        let values = wheelManager.telemetryBuffer.buffer.valuesFor(metric: metric)
        let arr = values.compactMap { ($0 as? NSNumber)?.doubleValue }
        return Array(arr.suffix(20))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Alarm banner
                if !wheelManager.activeAlarms.isEmpty {
                    AlarmBannerView(activeAlarms: wheelManager.activeAlarms)
                }

                // Speed gauge (tappable)
                Button(action: { selectedMetric = "speed" }) {
                    SpeedGaugeView(
                        speed: displaySpeed,
                        maxSpeed: maxSpeed,
                        unitLabel: speedUnit
                    )
                    .frame(height: 250)
                    .padding(.top)
                }
                .buttonStyle(.plain)

                // 2x3 Gauge Tile Grid
                let columns = [GridItem(.flexible()), GridItem(.flexible())]

                LazyVGrid(columns: columns, spacing: 12) {
                    // Speed tile
                    let speedVal = displaySpeed
                    GaugeTileView(
                        label: "Speed",
                        value: String(format: "%.1f", speedVal),
                        unit: speedUnit,
                        progress: speedVal / maxSpeed,
                        color: tileColor(metric: .speed, value: wheelManager.wheelState.speedKmh),
                        sparklineData: sparkline(metric: .speed),
                        action: { selectedMetric = "speed" }
                    )

                    // Battery tile
                    let batteryVal = Double(wheelManager.wheelState.batteryLevel)
                    GaugeTileView(
                        label: "Battery",
                        value: "\(wheelManager.wheelState.batteryLevel)",
                        unit: "%",
                        progress: batteryVal / 100.0,
                        color: tileColor(metric: .battery, value: batteryVal),
                        sparklineData: sparkline(metric: .battery),
                        action: { selectedMetric = "battery" }
                    )

                    // Power tile
                    let powerVal = wheelManager.wheelState.powerW
                    let powerMax = wheelManager.telemetryBuffer.buffer.effectiveMax(metric: .power)
                    GaugeTileView(
                        label: "Power",
                        value: String(format: "%.0f", powerVal),
                        unit: "W",
                        progress: powerMax > 0 ? abs(powerVal) / powerMax : 0,
                        color: tileColor(metric: .power, value: abs(powerVal)),
                        sparklineData: sparkline(metric: .power),
                        action: { selectedMetric = "power" }
                    )

                    // PWM tile
                    let pwmVal = wheelManager.wheelState.pwmPercent
                    GaugeTileView(
                        label: "PWM",
                        value: String(format: "%.1f", pwmVal),
                        unit: "%",
                        progress: pwmVal / 100.0,
                        color: tileColor(metric: .pwm, value: pwmVal),
                        sparklineData: sparkline(metric: .pwm),
                        action: { selectedMetric = "pwm" }
                    )

                    // Temperature tile
                    let tempC = Double(wheelManager.wheelState.temperatureC)
                    let tempDisplay = DisplayUtils.shared.convertTemp(celsius: tempC, useFahrenheit: wheelManager.useFahrenheit)
                    let tempUnit = DisplayUtils.shared.temperatureUnit(useFahrenheit: wheelManager.useFahrenheit)
                    GaugeTileView(
                        label: "Temp",
                        value: String(format: "%.0f", tempDisplay),
                        unit: tempUnit,
                        progress: tempC / 80.0,
                        color: tileColor(metric: .temperature, value: tempC),
                        sparklineData: sparkline(metric: .temperature),
                        action: { selectedMetric = "temperature" }
                    )

                    // GPS Speed tile
                    let gpsSpeedRaw = wheelManager.locationManager.currentLocation?.speed ?? 0
                    let gpsKmh = ByteUtils.shared.metersPerSecondToKmh(speedMs: max(0, gpsSpeedRaw))
                    let gpsDisplay = DisplayUtils.shared.convertSpeed(kmh: gpsKmh, useMph: wheelManager.useMph)
                    GaugeTileView(
                        label: "GPS Speed",
                        value: gpsKmh > 0 ? String(format: "%.1f", gpsDisplay) : "\u{2014}",
                        unit: speedUnit,
                        progress: gpsDisplay / maxSpeed,
                        color: tileColor(metric: .gpsSpeed, value: gpsKmh),
                        sparklineData: sparkline(metric: .gpsSpeed),
                        action: { selectedMetric = "gps_speed" }
                    )
                }
                .padding(.horizontal)

                // Compact stats row
                VStack(spacing: 12) {
                    StatRow(label: "Voltage", value: String(format: "%.1f V", wheelManager.wheelState.voltageV))
                    StatRow(label: "Current", value: String(format: "%.1f A", wheelManager.wheelState.currentA))
                    StatRow(label: "Trip Distance", value: DisplayUtils.shared.formatDistance(km: wheelManager.wheelState.wheelDistanceKm, useMph: wheelManager.useMph, decimals: 2))
                    StatRow(label: "Total Distance", value: DisplayUtils.shared.formatDistance(km: wheelManager.wheelState.totalDistanceKm, useMph: wheelManager.useMph, decimals: 1))
                }
                .padding()
                .background(Color(UIColor.secondarySystemGroupedBackground))
                .cornerRadius(12)
                .padding(.horizontal)

                // Wheel settings (navigate to full control panel)
                if wheelManager.wheelState.pedalsMode >= 0 {
                    NavigationLink(destination: WheelSettingsView()) {
                        VStack(spacing: 12) {
                            StatRow(label: "Pedals Mode", value: DisplayUtils.shared.pedalsModeText(mode: wheelManager.wheelState.pedalsMode))
                            StatRow(label: "Tilt-Back Speed", value: DisplayUtils.shared.tiltBackSpeedText(speed: wheelManager.wheelState.tiltBackSpeed, useMph: wheelManager.useMph))
                            StatRow(label: "Light", value: DisplayUtils.shared.lightModeText(mode: wheelManager.wheelState.lightMode))
                            StatRow(label: "LED Mode", value: "\(wheelManager.wheelState.ledMode)")
                        }
                        .padding()
                        .background(Color(UIColor.secondarySystemGroupedBackground))
                        .cornerRadius(12)
                        .padding(.horizontal)
                    }
                    .buttonStyle(.plain)
                }

                // Wheel info
                if !wheelManager.wheelState.name.isEmpty || !wheelManager.wheelState.model.isEmpty {
                    VStack(spacing: 12) {
                        if !wheelManager.wheelState.name.isEmpty {
                            StatRow(label: "Name", value: wheelManager.wheelState.name)
                        }
                        if !wheelManager.wheelState.model.isEmpty {
                            StatRow(label: "Model", value: wheelManager.wheelState.model)
                        }
                        StatRow(label: "Type", value: wheelManager.wheelState.wheelType.name)
                        if !wheelManager.wheelState.version.isEmpty {
                            StatRow(label: "Firmware", value: wheelManager.wheelState.version)
                        }
                    }
                    .padding()
                    .background(Color(UIColor.secondarySystemGroupedBackground))
                    .cornerRadius(12)
                    .padding(.horizontal)
                }

                // Demo/Test mode indicator
                if wheelManager.isMockMode {
                    HStack {
                        Image(systemName: "info.circle.fill")
                        Text("Demo Mode - Simulated Data")
                    }
                    .font(.caption)
                    .foregroundColor(.orange)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 16)
                    .background(Color.orange.opacity(0.15))
                    .cornerRadius(8)
                } else if wheelManager.isTestMode {
                    HStack {
                        Image(systemName: "testtube.2")
                        Text("Test Mode - KMP Decoder")
                    }
                    .font(.caption)
                    .foregroundColor(.blue)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 16)
                    .background(Color.blue.opacity(0.15))
                    .cornerRadius(8)
                }

                // Controls row: Horn, Light
                if !wheelManager.isMockMode && !wheelManager.isTestMode {
                    HStack(spacing: 12) {
                        Button(action: { wheelManager.wheelBeep() }) {
                            HStack {
                                Image(systemName: "speaker.wave.2.fill")
                                Text("Horn")
                            }
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.blue)
                            .cornerRadius(12)
                        }

                        Button(action: { wheelManager.toggleLight() }) {
                            HStack {
                                Image(systemName: wheelManager.isLightOn ? "lightbulb.fill" : "lightbulb")
                                Text("Light")
                            }
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(wheelManager.isLightOn ? Color.yellow : Color.blue)
                            .cornerRadius(12)
                        }
                    }
                    .padding(.horizontal)
                }

                // Record, Chart, BMS row
                HStack(spacing: 12) {
                    if wheelManager.connectionState.isConnected {
                        Button(action: {
                            if wheelManager.isLogging {
                                wheelManager.stopLogging()
                            } else {
                                wheelManager.startLogging()
                            }
                        }) {
                            HStack {
                                Image(systemName: wheelManager.isLogging ? "stop.circle.fill" : "record.circle")
                                Text(wheelManager.isLogging ? "Stop" : "Record")
                            }
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(wheelManager.isLogging ? Color.red : Color.gray)
                            .cornerRadius(12)
                        }
                    }

                    Button(action: { showBms = true }) {
                        HStack {
                            Image(systemName: "battery.100")
                            Text("BMS")
                        }
                        .fontWeight(.medium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.gray)
                        .cornerRadius(12)
                    }

                    Button(action: { showChart = true }) {
                        HStack {
                            Image(systemName: "chart.xyaxis.line")
                            Text("Chart")
                        }
                        .fontWeight(.medium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.purple)
                        .cornerRadius(12)
                    }
                }
                .padding(.horizontal)

                // Disconnect button
                Button(action: {
                    if wheelManager.isMockMode {
                        wheelManager.stopMockMode()
                    } else if wheelManager.isTestMode {
                        wheelManager.stopTestMode()
                    } else {
                        wheelManager.disconnect()
                    }
                }) {
                    Text(buttonLabel)
                        .fontWeight(.medium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(buttonColor)
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.bottom, 20)
            }
        }
        .background(Color(UIColor.systemGroupedBackground))
        .navigationTitle(wheelManager.wheelState.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $showChart) {
            TelemetryChartView()
        }
        .navigationDestination(isPresented: $showBms) {
            SmartBmsView()
        }
        .navigationDestination(isPresented: showMetricBinding) {
            MetricDetailView(metricId: selectedMetric ?? "speed")
        }
    }

    private var showMetricBinding: Binding<Bool> {
        Binding(
            get: { selectedMetric != nil },
            set: { if !$0 { selectedMetric = nil } }
        )
    }

    private var buttonLabel: String {
        if wheelManager.isMockMode { return "Stop Demo" }
        if wheelManager.isTestMode { return "Stop Test" }
        return "Disconnect"
    }

    private var buttonColor: Color {
        if wheelManager.isMockMode { return .orange }
        if wheelManager.isTestMode { return .blue }
        return .red
    }
}

// MARK: - Alarm Banner

struct AlarmBannerView: View {
    let activeAlarms: Set<AlarmType>

    @State private var isPulsing = false

    private var alarmText: String {
        let types = activeAlarms.sorted { $0.value < $1.value }
        return types.map { $0.displayName }.joined(separator: ", ")
    }

    var body: some View {
        HStack {
            Image(systemName: "exclamationmark.triangle.fill")
            Text("ALARM: \(alarmText)")
                .fontWeight(.bold)
        }
        .font(.subheadline)
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(
            isPulsing ? Color.red : Color.orange
        )
        .cornerRadius(8)
        .padding(.horizontal)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) {
                isPulsing = true
            }
        }
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title)
                .foregroundColor(color)

            Text(value)
                .font(.title2)
                .fontWeight(.bold)

            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(12)
    }
}

struct StatRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
        }
    }
}

#Preview {
    NavigationStack {
        DashboardView()
            .environmentObject(WheelManager())
    }
}
