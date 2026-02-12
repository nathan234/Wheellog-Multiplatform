import SwiftUI

struct DashboardView: View {
    @EnvironmentObject var wheelManager: WheelManager

    private let kmToMiles = 0.62137119223733

    private var displaySpeed: Double {
        wheelManager.useMph
            ? wheelManager.wheelState.speedKmh * kmToMiles
            : wheelManager.wheelState.speedKmh
    }

    private var speedUnit: String {
        wheelManager.useMph ? "mph" : "km/h"
    }

    private var displayTemperature: String {
        let tempC = wheelManager.wheelState.temperature
        if wheelManager.useFahrenheit {
            let tempF = Double(tempC) * 9.0 / 5.0 + 32
            return String(format: "%.0f°F", tempF)
        }
        return "\(tempC)°C"
    }

    private func formatDistance(_ km: Double) -> String {
        if wheelManager.useMph {
            return String(format: "%.2f mi", km * kmToMiles)
        }
        return String(format: "%.2f km", km)
    }

    private func formatTotalDistance(_ km: Double) -> String {
        if wheelManager.useMph {
            return String(format: "%.1f mi", km * kmToMiles)
        }
        return String(format: "%.1f km", km)
    }

    private var pedalsModeText: String {
        switch wheelManager.wheelState.pedalsMode {
        case 0: return "Hard"
        case 1: return "Medium"
        case 2: return "Soft"
        default: return "Unknown"
        }
    }

    private var lightModeText: String {
        switch wheelManager.wheelState.lightMode {
        case 0: return "Off"
        case 1: return "On"
        case 2: return "Strobe"
        default: return "Unknown"
        }
    }

    private var tiltBackSpeedText: String {
        let speed = wheelManager.wheelState.tiltBackSpeed
        if speed == 0 { return "Off" }
        if wheelManager.useMph {
            return String(format: "%.0f mph", Double(speed) * kmToMiles)
        }
        return "\(speed) km/h"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Speed gauge
                SpeedGaugeView(
                    speed: displaySpeed,
                    maxSpeed: wheelManager.useMph ? 31.0 : 50.0,
                    unitLabel: speedUnit
                )
                    .frame(height: 250)
                    .padding(.top)

                // Battery and temperature
                HStack(spacing: 16) {
                    StatCard(
                        title: "Battery",
                        value: "\(wheelManager.wheelState.batteryLevel)%",
                        icon: batteryIcon,
                        color: batteryColor
                    )

                    StatCard(
                        title: "Temperature",
                        value: displayTemperature,
                        icon: "thermometer",
                        color: temperatureColor
                    )
                }
                .padding(.horizontal)

                // Power stats
                VStack(spacing: 12) {
                    StatRow(label: "Voltage", value: String(format: "%.1f V", wheelManager.wheelState.voltage))
                    StatRow(label: "Current", value: String(format: "%.1f A", wheelManager.wheelState.current))
                    StatRow(label: "Power", value: String(format: "%.0f W", wheelManager.wheelState.power))
                    StatRow(label: "PWM", value: String(format: "%.1f%%", wheelManager.wheelState.pwmPercent))
                }
                .padding()
                .background(Color(.secondarySystemGroupedBackground))
                .cornerRadius(12)
                .padding(.horizontal)

                // Distance stats
                VStack(spacing: 12) {
                    StatRow(label: "Trip Distance", value: formatDistance(wheelManager.wheelState.wheelDistanceKm))
                    StatRow(label: "Total Distance", value: formatTotalDistance(wheelManager.wheelState.totalDistanceKm))
                }
                .padding()
                .background(Color(.secondarySystemGroupedBackground))
                .cornerRadius(12)
                .padding(.horizontal)

                // Wheel settings (only show if settings have been received)
                if wheelManager.wheelState.pedalsMode >= 0 {
                    VStack(spacing: 12) {
                        StatRow(label: "Pedals Mode", value: pedalsModeText)
                        StatRow(label: "Tilt-Back Speed", value: tiltBackSpeedText)
                        StatRow(label: "Light", value: lightModeText)
                        StatRow(label: "LED Mode", value: "\(wheelManager.wheelState.ledMode)")
                    }
                    .padding()
                    .background(Color(.secondarySystemGroupedBackground))
                    .cornerRadius(12)
                    .padding(.horizontal)
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
                        StatRow(label: "Type", value: wheelManager.wheelState.wheelType)
                    }
                    .padding()
                    .background(Color(.secondarySystemGroupedBackground))
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

                // Horn and Light controls
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
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Dashboard")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var batteryIcon: String {
        let level = wheelManager.wheelState.batteryLevel
        if level >= 75 { return "battery.100" }
        if level >= 50 { return "battery.75" }
        if level >= 25 { return "battery.50" }
        return "battery.25"
    }

    private var batteryColor: Color {
        let level = wheelManager.wheelState.batteryLevel
        if level >= 50 { return .green }
        if level >= 25 { return .orange }
        return .red
    }

    private var temperatureColor: Color {
        // Thresholds are in °C regardless of display unit
        let tempC = wheelManager.wheelState.temperature
        if tempC <= 40 { return .green }
        if tempC <= 55 { return .orange }
        return .red
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
        .background(Color(.secondarySystemGroupedBackground))
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
