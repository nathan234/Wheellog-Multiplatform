import SwiftUI
import WheelLogCore

struct SettingsView: View {
    @EnvironmentObject var wheelManager: WheelManager

    private let kmToMiles = 0.62137119223733

    var body: some View {
        Form {
            // MARK: - Units
            Section("Units") {
                Toggle("Use Miles per Hour", isOn: $wheelManager.useMph)
                Toggle("Use Fahrenheit", isOn: $wheelManager.useFahrenheit)
            }

            // MARK: - Speed & Safety Alarms
            Section {
                Toggle("Enable Alarms", isOn: $wheelManager.alarmsEnabled)

                if wheelManager.alarmsEnabled {
                    // Alarm action picker
                    Picker("Alarm Action", selection: $wheelManager.alarmAction) {
                        ForEach(AlarmAction.allCases, id: \.self) { action in
                            Text(action.label).tag(action)
                        }
                    }

                    Toggle("PWM-Based Alarms", isOn: $wheelManager.pwmBasedAlarms)
                }
            } header: {
                Text("Speed & Safety Alarms")
            } footer: {
                if wheelManager.alarmsEnabled {
                    Text(wheelManager.pwmBasedAlarms
                        ? "PWM mode triggers alarms based on motor load percentage."
                        : "Set to 0 to disable individual alarms.")
                }
            }

            // PWM-based alarm settings
            if wheelManager.alarmsEnabled && wheelManager.pwmBasedAlarms {
                Section("PWM Alarm Thresholds") {
                    alarmSlider(
                        label: "Alarm Factor 1",
                        value: $wheelManager.alarmFactor1,
                        range: 0...99,
                        displayValue: wheelManager.alarmFactor1,
                        unit: "%"
                    )
                    alarmSlider(
                        label: "Alarm Factor 2",
                        value: $wheelManager.alarmFactor2,
                        range: 0...99,
                        displayValue: wheelManager.alarmFactor2,
                        unit: "%"
                    )
                }

                Section {
                    alarmSlider(
                        label: "Warning Speed",
                        value: $wheelManager.warningSpeed,
                        range: 0...120,
                        displayValue: displaySpeed(wheelManager.warningSpeed),
                        unit: wheelManager.useMph ? "mph" : "km/h"
                    )
                    alarmSlider(
                        label: "Warning PWM",
                        value: $wheelManager.warningPwm,
                        range: 0...99,
                        displayValue: wheelManager.warningPwm,
                        unit: "%"
                    )
                    if wheelManager.warningSpeed > 0 || wheelManager.warningPwm > 0 {
                        alarmSlider(
                            label: "Warning Period",
                            value: $wheelManager.warningSpeedPeriod,
                            range: 0...60,
                            displayValue: wheelManager.warningSpeedPeriod,
                            unit: "sec"
                        )
                    }
                } header: {
                    Text("Pre-Warnings")
                } footer: {
                    Text("Advisory tones before full alarms. Set warning period > 0 to enable.")
                }
            }

            // Old-style speed alarm settings
            if wheelManager.alarmsEnabled && !wheelManager.pwmBasedAlarms {
                Section("Speed Alarms") {
                    alarmSlider(
                        label: "Alarm 1 Speed",
                        value: $wheelManager.alarm1Speed,
                        range: 0...100,
                        displayValue: displaySpeed(wheelManager.alarm1Speed),
                        unit: wheelManager.useMph ? "mph" : "km/h"
                    )
                    alarmSlider(
                        label: "Alarm 1 Battery",
                        value: $wheelManager.alarm1Battery,
                        range: 0...100,
                        displayValue: wheelManager.alarm1Battery,
                        unit: "%"
                    )

                    alarmSlider(
                        label: "Alarm 2 Speed",
                        value: $wheelManager.alarm2Speed,
                        range: 0...100,
                        displayValue: displaySpeed(wheelManager.alarm2Speed),
                        unit: wheelManager.useMph ? "mph" : "km/h"
                    )
                    alarmSlider(
                        label: "Alarm 2 Battery",
                        value: $wheelManager.alarm2Battery,
                        range: 0...100,
                        displayValue: wheelManager.alarm2Battery,
                        unit: "%"
                    )

                    alarmSlider(
                        label: "Alarm 3 Speed",
                        value: $wheelManager.alarm3Speed,
                        range: 0...100,
                        displayValue: displaySpeed(wheelManager.alarm3Speed),
                        unit: wheelManager.useMph ? "mph" : "km/h"
                    )
                    alarmSlider(
                        label: "Alarm 3 Battery",
                        value: $wheelManager.alarm3Battery,
                        range: 0...100,
                        displayValue: wheelManager.alarm3Battery,
                        unit: "%"
                    )
                }
            }

            // Always-shown alarm types
            if wheelManager.alarmsEnabled {
                Section("Other Alarms") {
                    alarmSlider(
                        label: "Current Alarm",
                        value: $wheelManager.alarmCurrent,
                        range: 0...100,
                        displayValue: wheelManager.alarmCurrent,
                        unit: "A"
                    )
                    alarmSlider(
                        label: "Phase Current Alarm",
                        value: $wheelManager.alarmPhaseCurrent,
                        range: 0...400,
                        displayValue: wheelManager.alarmPhaseCurrent,
                        unit: "A"
                    )
                    alarmSlider(
                        label: "Temperature Alarm",
                        value: $wheelManager.alarmTemperature,
                        range: 0...80,
                        displayValue: displayTemperature(wheelManager.alarmTemperature),
                        unit: wheelManager.useFahrenheit ? "°F" : "°C"
                    )
                    alarmSlider(
                        label: "Motor Temp Alarm",
                        value: $wheelManager.alarmMotorTemperature,
                        range: 0...200,
                        displayValue: displayTemperature(wheelManager.alarmMotorTemperature),
                        unit: wheelManager.useFahrenheit ? "°F" : "°C"
                    )
                    alarmSlider(
                        label: "Battery Alarm",
                        value: $wheelManager.alarmBattery,
                        range: 0...100,
                        displayValue: wheelManager.alarmBattery,
                        unit: "%"
                    )
                    Toggle("Wheel Alarm", isOn: $wheelManager.alarmWheel)
                }
            }

            // MARK: - Logging (Feature 3)
            Section {
                Toggle("Auto-Start Logging", isOn: $wheelManager.autoStartLogging)
                Toggle("Include GPS", isOn: $wheelManager.logGPS)
            } header: {
                Text("Logging")
            } footer: {
                Text("GPS requires location permission. Logs are saved as CSV files.")
            }

            // MARK: - Connection
            Section("Connection") {
                Toggle("Auto Reconnect", isOn: $wheelManager.autoReconnect)
                Toggle("Show Unknown Devices", isOn: $wheelManager.showUnknownDevices)
            }

            // MARK: - Wheel Settings
            if wheelManager.connectionState.isConnected {
                Section("Wheel Settings") {
                    NavigationLink("Wheel Control Panel", destination: WheelSettingsView())
                }
            }

            // MARK: - About
            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text(appVersion)
                        .foregroundColor(.secondary)
                }
                Link("GitHub Repository", destination: URL(string: AppConstants.shared.GITHUB_REPO_URL)!)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Alarm Slider Helper

    private func alarmSlider(
        label: String,
        value: Binding<Double>,
        range: ClosedRange<Double>,
        displayValue: Double,
        unit: String
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                Spacer()
                Text("\(Int(displayValue)) \(unit)")
                    .foregroundColor(.secondary)
            }
            Slider(value: value, in: range, step: 1)
        }
    }

    // MARK: - Unit Conversion Helpers

    private func displaySpeed(_ kmh: Double) -> Double {
        wheelManager.useMph ? kmh * kmToMiles : kmh
    }

    private func displayTemperature(_ celsius: Double) -> Double {
        wheelManager.useFahrenheit ? celsius * 9.0 / 5.0 + 32 : celsius
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(version) (\(build))"
    }
}

#Preview {
    NavigationStack {
        SettingsView()
            .environmentObject(WheelManager())
    }
}
