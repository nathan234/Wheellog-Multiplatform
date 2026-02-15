import Foundation
import AVFoundation
import UIKit
import WheelLogCore

/// Maps KMP AlarmType enum values to Swift for display and notification purposes.
enum AlarmDisplayType: String, CaseIterable, Hashable {
    case speed1, speed2, speed3, current, temperature, pwm, battery, wheel

    var displayName: String {
        switch self {
        case .speed1: return "Speed 1"
        case .speed2: return "Speed 2"
        case .speed3: return "Speed 3"
        case .current: return "Current"
        case .temperature: return "Temp"
        case .pwm: return "PWM"
        case .battery: return "Battery"
        case .wheel: return "Wheel"
        }
    }

    /// Map from KMP AlarmType to Swift AlarmDisplayType
    static func from(kmpAlarmType: AlarmType) -> AlarmDisplayType? {
        switch kmpAlarmType {
        case .speed1: return .speed1
        case .speed2: return .speed2
        case .speed3: return .speed3
        case .current: return .current
        case .temperature: return .temperature
        case .pwm: return .pwm
        case .battery: return .battery
        case .wheel: return .wheel
        default: return nil
        }
    }
}

enum AlarmAction: Int, CaseIterable {
    case phoneOnly = 0
    case phoneAndWheel = 1
    case all = 2

    var label: String {
        switch self {
        case .phoneOnly: return "Phone Only"
        case .phoneAndWheel: return "Phone + Wheel"
        case .all: return "All"
        }
    }
}

@MainActor
class AlarmManager: ObservableObject {

    @Published var activeAlarms: Set<AlarmDisplayType> = []

    var onAlarmFired: ((AlarmDisplayType, String) -> Void)?
    var sendWheelBeep: (() -> Void)?

    // KMP alarm checker (created once, reused)
    private let kmpChecker: AlarmChecker

    // Audio
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?

    // Haptics
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    nonisolated init() {
        kmpChecker = WheelConnectionManagerFactory.shared.createAlarmChecker()
    }

    // MARK: - KMP Alarm Check

    func checkAlarms(state: WheelState, config: AlarmConfig, enabled: Bool, action: AlarmAction) {
        guard enabled else {
            if !activeAlarms.isEmpty {
                activeAlarms = []
            }
            return
        }

        let currentTimeMs = Int64(Date().timeIntervalSince1970 * 1000)
        let result = WheelConnectionManagerFactory.shared.checkAlarms(
            checker: kmpChecker,
            state: state,
            config: config,
            currentTimeMs: currentTimeMs
        )

        processAlarmResult(result, action: action)
    }

    func reset() {
        WheelConnectionManagerFactory.shared.resetAlarmChecker(checker: kmpChecker)
        activeAlarms = []
    }

    // MARK: - Process Result

    private func processAlarmResult(_ result: AlarmResult, action: AlarmAction) {
        var newActive: Set<AlarmDisplayType> = []

        for alarm in result.triggeredAlarms {
            guard let displayType = AlarmDisplayType.from(kmpAlarmType: alarm.type) else { continue }
            newActive.insert(displayType)

            // Fire platform effects for each triggered alarm
            fireAlarm(
                type: displayType,
                action: action,
                toneDurationMs: Int(alarm.toneDuration)
            )
        }

        // Handle pre-warning (advisory tone, no haptic)
        if let preWarning = result.preWarning {
            playPreWarning(type: preWarning.type)
        }

        activeAlarms = newActive
    }

    // MARK: - Fire Alarm

    private func fireAlarm(type: AlarmDisplayType, action: AlarmAction, toneDurationMs: Int) {
        // Audio beep with KMP-computed duration
        let frequency: Float
        switch type {
        case .speed1, .speed2, .speed3, .pwm: frequency = 1000
        case .current: frequency = 800
        case .temperature: frequency = 600
        case .battery: frequency = 400
        case .wheel: frequency = 1200
        }

        setupAudioSessionIfNeeded()
        let duration = Float(toneDurationMs) / 1000.0
        generateTone(frequency: frequency, duration: max(duration, 0.02))

        // Haptic
        switch type {
        case .speed1, .speed2, .speed3, .current, .pwm, .wheel:
            heavyImpact.impactOccurred()
        case .temperature, .battery:
            notificationFeedback.notificationOccurred(.warning)
        }

        // Wheel beep (if action includes wheel)
        if action == .phoneAndWheel || action == .all {
            sendWheelBeep?()
        }

        // Notification callback (for background mode)
        let message = alarmMessage(for: type)
        onAlarmFired?(type, message)
    }

    // MARK: - Pre-Warning

    private func playPreWarning(type: PreWarningType) {
        setupAudioSessionIfNeeded()
        // Distinct advisory tone: lower volume, shorter, different frequency
        let frequency: Float = type == .pwm ? 700 : 500
        generateTone(frequency: frequency, duration: 0.1)
    }

    private func alarmMessage(for type: AlarmDisplayType) -> String {
        switch type {
        case .speed1: return "Speed alarm 1 triggered"
        case .speed2: return "Speed alarm 2 triggered"
        case .speed3: return "Speed alarm 3 triggered"
        case .current: return "Current alarm triggered"
        case .temperature: return "Temperature alarm triggered"
        case .pwm: return "PWM alarm triggered"
        case .battery: return "Low battery alarm triggered"
        case .wheel: return "Wheel alarm triggered"
        }
    }

    // MARK: - Audio

    private func setupAudioSessionIfNeeded() {
        guard audioEngine == nil else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, options: .mixWithOthers)
            try session.setActive(true)

            let engine = AVAudioEngine()
            let player = AVAudioPlayerNode()
            engine.attach(player)

            let format = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1)!
            engine.connect(player, to: engine.mainMixerNode, format: format)
            try engine.start()

            audioEngine = engine
            playerNode = player
        } catch {
            print("Audio setup failed: \(error)")
        }
    }

    private func generateTone(frequency: Float, duration: Float) {
        guard let engine = audioEngine, let player = playerNode else { return }

        let sampleRate: Float = 44100
        let frameCount = AVAudioFrameCount(sampleRate * duration)
        guard let buffer = AVAudioPCMBuffer(pcmFormat: engine.mainMixerNode.outputFormat(forBus: 0), frameCapacity: frameCount) else { return }
        buffer.frameLength = frameCount

        guard let channelData = buffer.floatChannelData?[0] else { return }
        for i in 0..<Int(frameCount) {
            channelData[i] = sin(2.0 * .pi * frequency * Float(i) / sampleRate) * 0.5
        }

        player.stop()
        player.scheduleBuffer(buffer, completionHandler: nil)
        player.play()
    }
}
