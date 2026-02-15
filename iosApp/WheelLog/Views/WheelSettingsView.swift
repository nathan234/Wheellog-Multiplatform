import SwiftUI

struct WheelSettingsView: View {
    @EnvironmentObject var wheelManager: WheelManager

    // Confirmation alerts
    @State private var showCalibrateAlert = false
    @State private var showPowerOffAlert = false
    @State private var showResetTripAlert = false
    @State private var showLockAlert = false

    var body: some View {
        Form {
            switch wheelManager.wheelState.wheelType {
            case "KINGSONG":
                kingsongSection
            case "GOTWAY", "GOTWAY_VIRTUAL":
                gotwaySection
            case "VETERAN":
                veteranSection
            case "NINEBOT_Z":
                ninebotZSection
            case "INMOTION":
                inmotionSection
            case "INMOTION_V2":
                inmotionV2Section
            default:
                Section {
                    Text("Connect to a wheel to see its settings.")
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationTitle("Wheel Settings")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Calibrate Wheel", isPresented: $showCalibrateAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Calibrate", role: .destructive) { wheelManager.calibrate() }
        } message: {
            Text("Place the wheel upright on a flat surface before calibrating. The wheel must be stationary.")
        }
        .alert("Power Off", isPresented: $showPowerOffAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Power Off", role: .destructive) { wheelManager.powerOff() }
        } message: {
            Text("Are you sure you want to power off the wheel?")
        }
        .alert("Reset Trip", isPresented: $showResetTripAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Reset", role: .destructive) { wheelManager.resetTrip() }
        } message: {
            Text("This will reset the trip distance counter to zero.")
        }
        .alert("Lock Wheel", isPresented: $showLockAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Lock", role: .destructive) { wheelManager.setLock(true) }
        } message: {
            Text("Locking the wheel will prevent it from riding. Unlock via this app.")
        }
    }

    // MARK: - KingSong

    @ViewBuilder
    private var kingsongSection: some View {
        Section("Lighting") {
            Picker("Light Mode", selection: lightModeBinding) {
                Text("Off").tag(0)
                Text("On").tag(1)
                Text("Auto").tag(2)
            }

            Picker("LED Mode", selection: ledModeBinding) {
                ForEach(0..<8, id: \.self) { i in
                    Text("\(i)").tag(i)
                }
            }

            Picker("Strobe Mode", selection: strobeModeBinding) {
                ForEach(0..<4, id: \.self) { i in
                    Text("\(i)").tag(i)
                }
            }
        }

        Section("Ride") {
            Picker("Pedals Mode", selection: pedalsModeBinding) {
                Text("Hard").tag(0)
                Text("Medium").tag(1)
                Text("Soft").tag(2)
            }
            .pickerStyle(.segmented)
        }

        Section("Dangerous Actions") {
            Button("Calibrate Wheel") { showCalibrateAlert = true }
                .foregroundColor(.red)
            Button("Power Off") { showPowerOffAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - Gotway / Begode

    @ViewBuilder
    private var gotwaySection: some View {
        Section("Lighting") {
            Picker("Light Mode", selection: lightModeBinding) {
                Text("Off").tag(0)
                Text("On").tag(1)
                Text("Strobe").tag(2)
            }

            Picker("LED Mode", selection: ledModeBinding) {
                ForEach(0..<10, id: \.self) { i in
                    Text("\(i)").tag(i)
                }
            }
        }

        Section("Ride") {
            Picker("Pedals Mode", selection: pedalsModeBinding) {
                Text("Hard").tag(0)
                Text("Medium").tag(1)
                Text("Soft").tag(2)
            }
            .pickerStyle(.segmented)

            Picker("Roll Angle", selection: rollAngleModeBinding) {
                Text("Low").tag(0)
                Text("Medium").tag(1)
                Text("High").tag(2)
            }
            .pickerStyle(.segmented)
        }

        Section("Speed") {
            SliderRow(label: "Max Speed", value: maxSpeedBinding, range: 0...99, unit: "km/h")
        }

        Section("Audio") {
            SliderRow(label: "Beeper Volume", value: beeperVolumeBinding, range: 1...9, unit: "")
        }

        Section("Dangerous Actions") {
            Button("Calibrate Wheel") { showCalibrateAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - Veteran

    @ViewBuilder
    private var veteranSection: some View {
        Section("Lighting") {
            Toggle("Headlight", isOn: lightToggleBinding)
        }

        Section("Ride") {
            Picker("Pedals Mode", selection: pedalsModeBinding) {
                Text("Hard").tag(0)
                Text("Medium").tag(1)
                Text("Soft").tag(2)
            }
            .pickerStyle(.segmented)
        }

        Section("Dangerous Actions") {
            Button("Reset Trip") { showResetTripAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - Ninebot Z

    @ViewBuilder
    private var ninebotZSection: some View {
        Section("Lighting") {
            Toggle("Headlight", isOn: lightToggleBinding)
            Toggle("DRL", isOn: drlBinding)
            Toggle("Tail Light", isOn: tailLightBinding)

            Picker("LED Mode", selection: ledModeBinding) {
                Text("Off").tag(0)
                ForEach(1..<8, id: \.self) { i in
                    Text("Type \(i)").tag(i)
                }
            }
        }

        Section("Ride") {
            Toggle("Handle Button", isOn: handleButtonBinding)
            Toggle("Brake Assistant", isOn: brakeAssistBinding)

            SliderRow(label: "Pedal Sensitivity", value: pedalSensitivityBinding, range: 0...4, unit: "")
        }

        Section("Audio") {
            SliderRow(label: "Speaker Volume", value: speakerVolumeBinding, range: 0...127, unit: "")
        }

        Section("Wheel Alarms") {
            Toggle("Alarm 1", isOn: alarm1EnabledBinding)
            if alarm1EnabledBinding.wrappedValue {
                SliderRow(label: "Alarm 1 Speed", value: alarm1SpeedBinding, range: 0...60, unit: "km/h")
            }

            Toggle("Alarm 2", isOn: alarm2EnabledBinding)
            if alarm2EnabledBinding.wrappedValue {
                SliderRow(label: "Alarm 2 Speed", value: alarm2SpeedBinding, range: 0...60, unit: "km/h")
            }

            Toggle("Alarm 3", isOn: alarm3EnabledBinding)
            if alarm3EnabledBinding.wrappedValue {
                SliderRow(label: "Alarm 3 Speed", value: alarm3SpeedBinding, range: 0...60, unit: "km/h")
            }
        }

        Section("Speed Limit") {
            Toggle("Limited Mode", isOn: limitedModeBinding)
            if limitedModeBinding.wrappedValue {
                SliderRow(label: "Limited Speed", value: limitedSpeedBinding, range: 0...65, unit: "km/h")
            }
        }

        Section("Dangerous Actions") {
            Toggle("Lock Wheel", isOn: lockBinding)
            Button("Calibrate Wheel") { showCalibrateAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - InMotion V1

    @ViewBuilder
    private var inmotionSection: some View {
        Section("Lighting") {
            Toggle("Headlight", isOn: lightToggleBinding)
            Toggle("LEDs", isOn: ledToggleBinding)
        }

        Section("Ride") {
            Toggle("Handle Button", isOn: handleButtonBinding)
            Toggle("Ride Mode", isOn: rideModeBinding)

            SliderRow(label: "Max Speed", value: maxSpeedBinding, range: 3...60, unit: "km/h")
            SliderRow(label: "Pedal Tilt", value: pedalTiltBinding, range: -8...8, unit: "\u{00B0}")
            SliderRow(label: "Pedal Sensitivity", value: pedalSensitivityBinding, range: 4...100, unit: "%")
        }

        Section("Audio") {
            SliderRow(label: "Speaker Volume", value: speakerVolumeBinding, range: 0...100, unit: "")
        }

        Section("Dangerous Actions") {
            Button("Calibrate Wheel") { showCalibrateAlert = true }
                .foregroundColor(.red)
            Button("Power Off") { showPowerOffAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - InMotion V2

    @ViewBuilder
    private var inmotionV2Section: some View {
        Section("Lighting") {
            Toggle("Headlight", isOn: lightToggleBinding)
            Toggle("DRL", isOn: drlBinding)

            SliderRow(label: "Brightness", value: lightBrightnessBinding, range: 0...100, unit: "%")
        }

        Section("Ride") {
            Toggle("Handle Button", isOn: handleButtonBinding)
            Toggle("Ride Mode", isOn: rideModeBinding)
            Toggle("Go Home Mode", isOn: goHomeModeBinding)
            Toggle("Fancier Mode", isOn: fancierModeBinding)
            Toggle("Transport Mode", isOn: transportModeBinding)

            SliderRow(label: "Max Speed", value: maxSpeedBinding, range: 3...60, unit: "km/h")
            SliderRow(label: "Pedal Tilt", value: pedalTiltBinding, range: -10...10, unit: "\u{00B0}")
            SliderRow(label: "Pedal Sensitivity", value: pedalSensitivityBinding, range: 0...100, unit: "%")
        }

        Section("Thermal") {
            Toggle("Fan", isOn: fanBinding)
            Toggle("Fan Quiet Mode", isOn: fanQuietBinding)
        }

        Section("Audio") {
            SliderRow(label: "Speaker Volume", value: speakerVolumeBinding, range: 0...100, unit: "")
            Toggle("Mute", isOn: muteBinding)
        }

        Section("Dangerous Actions") {
            Toggle("Lock Wheel", isOn: lockBinding)
            Button("Calibrate Wheel") { showCalibrateAlert = true }
                .foregroundColor(.red)
            Button("Power Off") { showPowerOffAlert = true }
                .foregroundColor(.red)
        }
    }

    // MARK: - Bindings

    // Pedals mode (all brands)
    private var pedalsModeBinding: Binding<Int> {
        Binding(
            get: { Int(wheelManager.wheelState.pedalsMode) },
            set: { wheelManager.setPedalsMode($0) }
        )
    }

    // Light mode picker (Kingsong, Gotway)
    private var lightModeBinding: Binding<Int> {
        Binding(
            get: { Int(wheelManager.wheelState.lightMode) },
            set: { wheelManager.setLightMode($0) }
        )
    }

    // Light on/off toggle (Veteran, NinebotZ, InMotion)
    private var lightToggleBinding: Binding<Bool> {
        Binding(
            get: { wheelManager.wheelState.lightMode > 0 },
            set: { _ in wheelManager.toggleLight() }
        )
    }

    // LED mode picker
    private var ledModeBinding: Binding<Int> {
        Binding(
            get: { max(0, Int(wheelManager.wheelState.ledMode)) },
            set: { wheelManager.setLedMode($0) }
        )
    }

    // LED on/off toggle (InMotion V1)
    private var ledToggleBinding: Binding<Bool> {
        Binding(
            get: { wheelManager.wheelState.ledMode > 0 },
            set: { wheelManager.setLed($0) }
        )
    }

    // Strobe mode (Kingsong)
    @State private var strobeMode: Int = 0
    private var strobeModeBinding: Binding<Int> {
        Binding(
            get: { strobeMode },
            set: { newValue in
                strobeMode = newValue
                wheelManager.setStrobeMode(newValue)
            }
        )
    }

    // Roll angle (Gotway)
    @State private var rollAngleMode: Int = 0
    private var rollAngleModeBinding: Binding<Int> {
        Binding(
            get: { rollAngleMode },
            set: { newValue in
                rollAngleMode = newValue
                wheelManager.setRollAngleMode(newValue)
            }
        )
    }

    // Max speed slider
    @State private var maxSpeed: Double = 30
    private var maxSpeedBinding: Binding<Double> {
        Binding(
            get: { maxSpeed },
            set: { newValue in
                maxSpeed = newValue
                wheelManager.setMaxSpeed(Int(newValue))
            }
        )
    }

    // Beeper volume (Gotway)
    @State private var beeperVolume: Double = 5
    private var beeperVolumeBinding: Binding<Double> {
        Binding(
            get: { beeperVolume },
            set: { newValue in
                beeperVolume = newValue
                wheelManager.setBeeperVolume(Int(newValue))
            }
        )
    }

    // DRL toggle (NinebotZ, InMotion V2)
    @State private var drlEnabled: Bool = false
    private var drlBinding: Binding<Bool> {
        Binding(
            get: { drlEnabled },
            set: { newValue in
                drlEnabled = newValue
                wheelManager.setDrl(newValue)
            }
        )
    }

    // Tail light toggle (NinebotZ)
    @State private var tailLightEnabled: Bool = false
    private var tailLightBinding: Binding<Bool> {
        Binding(
            get: { tailLightEnabled },
            set: { newValue in
                tailLightEnabled = newValue
                wheelManager.setTailLight(newValue)
            }
        )
    }

    // Handle button toggle
    @State private var handleButtonEnabled: Bool = false
    private var handleButtonBinding: Binding<Bool> {
        Binding(
            get: { handleButtonEnabled },
            set: { newValue in
                handleButtonEnabled = newValue
                wheelManager.setHandleButton(newValue)
            }
        )
    }

    // Brake assist toggle (NinebotZ)
    @State private var brakeAssistEnabled: Bool = false
    private var brakeAssistBinding: Binding<Bool> {
        Binding(
            get: { brakeAssistEnabled },
            set: { newValue in
                brakeAssistEnabled = newValue
                wheelManager.setBrakeAssist(newValue)
            }
        )
    }

    // Pedal sensitivity slider
    @State private var pedalSensitivity: Double = 0
    private var pedalSensitivityBinding: Binding<Double> {
        Binding(
            get: { pedalSensitivity },
            set: { newValue in
                pedalSensitivity = newValue
                wheelManager.setPedalSensitivity(Int(newValue))
            }
        )
    }

    // Speaker volume slider
    @State private var speakerVolume: Double = 50
    private var speakerVolumeBinding: Binding<Double> {
        Binding(
            get: { speakerVolume },
            set: { newValue in
                speakerVolume = newValue
                wheelManager.setSpeakerVolume(Int(newValue))
            }
        )
    }

    // Alarm enabled toggles (NinebotZ)
    @State private var alarm1Enabled: Bool = false
    private var alarm1EnabledBinding: Binding<Bool> {
        Binding(
            get: { alarm1Enabled },
            set: { newValue in
                alarm1Enabled = newValue
                wheelManager.setAlarmEnabled(newValue, num: 1)
            }
        )
    }

    @State private var alarm2Enabled: Bool = false
    private var alarm2EnabledBinding: Binding<Bool> {
        Binding(
            get: { alarm2Enabled },
            set: { newValue in
                alarm2Enabled = newValue
                wheelManager.setAlarmEnabled(newValue, num: 2)
            }
        )
    }

    @State private var alarm3Enabled: Bool = false
    private var alarm3EnabledBinding: Binding<Bool> {
        Binding(
            get: { alarm3Enabled },
            set: { newValue in
                alarm3Enabled = newValue
                wheelManager.setAlarmEnabled(newValue, num: 3)
            }
        )
    }

    // Alarm speed sliders (NinebotZ)
    @State private var alarm1Speed: Double = 30
    private var alarm1SpeedBinding: Binding<Double> {
        Binding(
            get: { alarm1Speed },
            set: { newValue in
                alarm1Speed = newValue
                wheelManager.setAlarmSpeed(Int(newValue), num: 1)
            }
        )
    }

    @State private var alarm2Speed: Double = 35
    private var alarm2SpeedBinding: Binding<Double> {
        Binding(
            get: { alarm2Speed },
            set: { newValue in
                alarm2Speed = newValue
                wheelManager.setAlarmSpeed(Int(newValue), num: 2)
            }
        )
    }

    @State private var alarm3Speed: Double = 40
    private var alarm3SpeedBinding: Binding<Double> {
        Binding(
            get: { alarm3Speed },
            set: { newValue in
                alarm3Speed = newValue
                wheelManager.setAlarmSpeed(Int(newValue), num: 3)
            }
        )
    }

    // Limited mode (NinebotZ)
    @State private var limitedModeEnabled: Bool = false
    private var limitedModeBinding: Binding<Bool> {
        Binding(
            get: { limitedModeEnabled },
            set: { newValue in
                limitedModeEnabled = newValue
                wheelManager.setLimitedMode(newValue)
            }
        )
    }

    @State private var limitedSpeed: Double = 25
    private var limitedSpeedBinding: Binding<Double> {
        Binding(
            get: { limitedSpeed },
            set: { newValue in
                limitedSpeed = newValue
                wheelManager.setLimitedSpeed(Int(newValue))
            }
        )
    }

    // Lock toggle (NinebotZ, InMotion V2)
    @State private var lockEnabled: Bool = false
    private var lockBinding: Binding<Bool> {
        Binding(
            get: { lockEnabled },
            set: { newValue in
                if newValue {
                    showLockAlert = true
                } else {
                    lockEnabled = false
                    wheelManager.setLock(false)
                }
            }
        )
    }

    // Pedal tilt (InMotion)
    @State private var pedalTilt: Double = 0
    private var pedalTiltBinding: Binding<Double> {
        Binding(
            get: { pedalTilt },
            set: { newValue in
                pedalTilt = newValue
                wheelManager.setPedalTilt(Int(newValue))
            }
        )
    }

    // Ride mode (InMotion)
    @State private var rideModeEnabled: Bool = false
    private var rideModeBinding: Binding<Bool> {
        Binding(
            get: { rideModeEnabled },
            set: { newValue in
                rideModeEnabled = newValue
                wheelManager.setRideMode(newValue)
            }
        )
    }

    // Light brightness (InMotion V2)
    @State private var lightBrightness: Double = 50
    private var lightBrightnessBinding: Binding<Double> {
        Binding(
            get: { lightBrightness },
            set: { newValue in
                lightBrightness = newValue
                wheelManager.setLightBrightness(Int(newValue))
            }
        )
    }

    // Go Home mode (InMotion V2)
    @State private var goHomeModeEnabled: Bool = false
    private var goHomeModeBinding: Binding<Bool> {
        Binding(
            get: { goHomeModeEnabled },
            set: { newValue in
                goHomeModeEnabled = newValue
                wheelManager.setGoHomeMode(newValue)
            }
        )
    }

    // Fancier mode (InMotion V2)
    @State private var fancierModeEnabled: Bool = false
    private var fancierModeBinding: Binding<Bool> {
        Binding(
            get: { fancierModeEnabled },
            set: { newValue in
                fancierModeEnabled = newValue
                wheelManager.setFancierMode(newValue)
            }
        )
    }

    // Transport mode (InMotion V2)
    @State private var transportModeEnabled: Bool = false
    private var transportModeBinding: Binding<Bool> {
        Binding(
            get: { transportModeEnabled },
            set: { newValue in
                transportModeEnabled = newValue
                wheelManager.setTransportMode(newValue)
            }
        )
    }

    // Fan (InMotion V2)
    @State private var fanEnabled: Bool = false
    private var fanBinding: Binding<Bool> {
        Binding(
            get: { fanEnabled },
            set: { newValue in
                fanEnabled = newValue
                wheelManager.setFan(newValue)
            }
        )
    }

    // Fan quiet (InMotion V2)
    @State private var fanQuietEnabled: Bool = false
    private var fanQuietBinding: Binding<Bool> {
        Binding(
            get: { fanQuietEnabled },
            set: { newValue in
                fanQuietEnabled = newValue
                wheelManager.setFanQuiet(newValue)
            }
        )
    }

    // Mute (InMotion V2)
    @State private var muteEnabled: Bool = false
    private var muteBinding: Binding<Bool> {
        Binding(
            get: { muteEnabled },
            set: { newValue in
                muteEnabled = newValue
                wheelManager.setMute(newValue)
            }
        )
    }
}

// MARK: - Slider Row

private struct SliderRow: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let unit: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                Spacer()
                Text("\(Int(value))\(unit.isEmpty ? "" : " \(unit)")")
                    .foregroundColor(.secondary)
            }
            Slider(value: $value, in: range, step: 1)
        }
    }
}

// MARK: - Preview

#Preview("KingSong") {
    NavigationStack {
        WheelSettingsView()
            .environmentObject(WheelManager())
    }
}
