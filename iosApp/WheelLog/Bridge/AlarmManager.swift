import Foundation
import AVFoundation
import UIKit

enum AlarmType: String, CaseIterable, Hashable {
    case speed1, speed2, speed3, current, temperature, battery
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

    @Published var activeAlarms: Set<AlarmType> = []

    var onAlarmFired: ((AlarmType, String) -> Void)?
    var sendWheelBeep: (() -> Void)?

    private var lastFiredTime: [AlarmType: Date] = [:]
    private let cooldownInterval: TimeInterval = 5.0

    // Audio
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?

    // Haptics
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    nonisolated init() {}

    // MARK: - Alarm Check

    struct AlarmSettings {
        let enabled: Bool
        let alarm1Speed: Double
        let alarm2Speed: Double
        let alarm3Speed: Double
        let alarmCurrent: Double
        let alarmTemperature: Double
        let alarmBattery: Double
        let action: AlarmAction
    }

    struct WheelValues {
        let speedKmh: Double
        let current: Double
        let temperature: Int
        let batteryLevel: Int
    }

    func checkAlarms(values: WheelValues, settings: AlarmSettings) {
        guard settings.enabled else {
            if !activeAlarms.isEmpty {
                activeAlarms = []
            }
            return
        }

        var newActive: Set<AlarmType> = []

        // Speed alarms
        if settings.alarm1Speed > 0, values.speedKmh >= settings.alarm1Speed {
            newActive.insert(.speed1)
        }
        if settings.alarm2Speed > 0, values.speedKmh >= settings.alarm2Speed {
            newActive.insert(.speed2)
        }
        if settings.alarm3Speed > 0, values.speedKmh >= settings.alarm3Speed {
            newActive.insert(.speed3)
        }

        // Current alarm
        if settings.alarmCurrent > 0, abs(values.current) >= settings.alarmCurrent {
            newActive.insert(.current)
        }

        // Temperature alarm
        if settings.alarmTemperature > 0, Double(values.temperature) >= settings.alarmTemperature {
            newActive.insert(.temperature)
        }

        // Battery alarm (inverted — alarm when LOW)
        if settings.alarmBattery > 0, Double(values.batteryLevel) <= settings.alarmBattery {
            newActive.insert(.battery)
        }

        // Fire alarms for newly triggered types
        let now = Date()
        for type in newActive {
            if !activeAlarms.contains(type) || canFire(type, now: now) {
                if canFire(type, now: now) {
                    fireAlarm(type: type, action: settings.action)
                    lastFiredTime[type] = now
                }
            }
        }

        activeAlarms = newActive
    }

    private func canFire(_ type: AlarmType, now: Date) -> Bool {
        guard let last = lastFiredTime[type] else { return true }
        return now.timeIntervalSince(last) >= cooldownInterval
    }

    // MARK: - Fire Alarm

    private func fireAlarm(type: AlarmType, action: AlarmAction) {
        // Audio beep
        playBeep(for: type)

        // Haptic
        switch type {
        case .speed1, .speed2, .speed3, .current:
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

    private func alarmMessage(for type: AlarmType) -> String {
        switch type {
        case .speed1: return "Speed alarm 1 triggered"
        case .speed2: return "Speed alarm 2 triggered"
        case .speed3: return "Speed alarm 3 triggered"
        case .current: return "Current alarm triggered"
        case .temperature: return "Temperature alarm triggered"
        case .battery: return "Low battery alarm triggered"
        }
    }

    // MARK: - Audio

    private func playBeep(for type: AlarmType) {
        let frequency: Float
        switch type {
        case .speed1, .speed2, .speed3: frequency = 1000
        case .current: frequency = 800
        case .temperature: frequency = 600
        case .battery: frequency = 400
        }

        setupAudioSessionIfNeeded()
        generateTone(frequency: frequency, duration: 0.2)
    }

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
