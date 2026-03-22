import Foundation
import AVFoundation
import UIKit
import FreeWheelCore

@MainActor
class AlarmManager: ObservableObject {

    @Published var activeAlarms: Set<AlarmType> = []

    var onAlarmFired: ((AlarmType, String) -> Void)?
    var sendWheelBeep: (() -> Void)?

    // KMP alarm checker (created once, reused)
    private let kmpChecker: AlarmChecker

    // Audio
    private var audioEngine: AVAudioEngine?
    private var playerNode: AVAudioPlayerNode?

    // KMP throttler — shared 500ms per-type notification suppression
    private let throttler: AlarmNotificationThrottler

    // Haptics
    private let heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private let notificationFeedback = UINotificationFeedbackGenerator()

    init() {
        kmpChecker = WheelConnectionManagerHelper.shared.createAlarmChecker()
        throttler = AlarmNotificationThrottler(throttleMs: 500) {
            KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000))
        }
    }

    // MARK: - KMP Alarm Check

    func checkTelemetry(telemetry: TelemetryState, config: AlarmConfig, enabled: Bool, action: FreeWheelCore.AlarmAction) {
        guard enabled else {
            if !activeAlarms.isEmpty {
                activeAlarms = []
            }
            return
        }

        let currentTimeMs = Int64(Date().timeIntervalSince1970 * 1000)
        let result = kmpChecker.check(telemetry: telemetry, config: config, currentTimeMs: currentTimeMs)

        processAlarmResult(result, action: action)
    }

    func reset() {
        WheelConnectionManagerHelper.shared.resetAlarmChecker(checker: kmpChecker)
        activeAlarms = []
        throttler.reset()
    }

    // MARK: - Process Result

    private func processAlarmResult(_ result: AlarmResult, action: FreeWheelCore.AlarmAction) {
        var newActive: Set<AlarmType> = []

        for alarm in result.triggeredAlarms {
            newActive.insert(alarm.type)

            // Throttle: skip if same alarm type fired within 500ms
            if throttler.isThrottled(type: alarm.type) {
                continue
            }
            throttler.recordFired(type: alarm.type)

            fireAlarm(
                type: alarm.type,
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

    private func fireAlarm(type: AlarmType, action: FreeWheelCore.AlarmAction, toneDurationMs: Int) {
        // Audio beep with KMP-computed duration
        let frequency = Float(type.audioFrequencyHz)

        setupAudioSessionIfNeeded()
        let duration = Float(toneDurationMs) / 1000.0
        generateTone(frequency: frequency, duration: max(duration, 0.02))

        // Haptic
        switch type {
        case .speed1, .speed2, .speed3, .current, .pwm, .wheel:
            heavyImpact.impactOccurred()
        case .temperature, .battery:
            notificationFeedback.notificationOccurred(.warning)
        default:
            heavyImpact.impactOccurred()
        }

        // Wheel beep (if action includes wheel)
        if action == .phoneAndWheel || action == .all {
            sendWheelBeep?()
        }

        // Notification callback (for background mode)
        onAlarmFired?(type, type.alarmMessage)
    }

    // MARK: - Pre-Warning

    private func playPreWarning(type: PreWarningType) {
        setupAudioSessionIfNeeded()
        // Distinct advisory tone: lower volume, shorter, different frequency
        let frequency = Float(type.audioFrequencyHz)
        generateTone(frequency: frequency, duration: 0.1)
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

            guard let format = AVAudioFormat(standardFormatWithSampleRate: 44100, channels: 1) else { return }
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
