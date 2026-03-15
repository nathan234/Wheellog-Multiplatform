import Foundation
import FreeWheelCore

// CROSS-PLATFORM SYNC: This mirrors freewheel/.../compose/components/RideReplay.kt (CsvReplayController).
// When modifying replay logic, update the counterpart.

class RideReplayController: ObservableObject {
    let samples: [TelemetrySample]

    @Published var isPlaying = false
    @Published var currentIndex = 0
    @Published var speedMultiplier: Float = 4.0
    @Published var isFinished = false

    private var task: Task<Void, Never>?

    var currentSample: TelemetrySample? {
        guard currentIndex >= 0, currentIndex < samples.count else { return nil }
        return samples[currentIndex]
    }

    var progress: Float {
        guard samples.count > 1 else { return 0 }
        return Float(currentIndex) / Float(samples.count - 1)
    }

    var totalDurationMs: Int64 {
        guard samples.count >= 2 else { return 0 }
        return samples.last!.timestampMs - samples.first!.timestampMs
    }

    var elapsedMs: Int64 {
        guard let first = samples.first?.timestampMs,
              let current = currentSample?.timestampMs else { return 0 }
        return current - first
    }

    init(samples: [TelemetrySample]) {
        self.samples = samples
    }

    func play() {
        guard !isPlaying else { return }
        if isFinished {
            currentIndex = 0
            isFinished = false
        }
        isPlaying = true
        task = Task { @MainActor [weak self] in
            guard let self else { return }
            while !Task.isCancelled && self.currentIndex < self.samples.count - 1 {
                let delayMs = self.samples[self.currentIndex + 1].timestampMs - self.samples[self.currentIndex].timestampMs
                let adjustedMs = max(Int64(Double(delayMs) / Double(self.speedMultiplier)), 10)
                try? await Task.sleep(nanoseconds: UInt64(adjustedMs) * 1_000_000)
                guard !Task.isCancelled else { return }
                self.currentIndex += 1
            }
            if self.currentIndex >= self.samples.count - 1 {
                self.isPlaying = false
                self.isFinished = true
            }
        }
    }

    func pause() {
        task?.cancel()
        task = nil
        isPlaying = false
    }

    func togglePlayPause() {
        if isPlaying { pause() } else { play() }
    }

    func seekTo(_ progress: Float) {
        if isPlaying { pause() }
        let idx = Int(progress * Float(samples.count - 1))
        currentIndex = max(0, min(idx, samples.count - 1))
        isFinished = false
    }

    func skipForward() {
        seekByMs(30_000)
    }

    func skipBackward() {
        seekByMs(-30_000)
    }

    private func seekByMs(_ deltaMs: Int64) {
        let wasPlaying = isPlaying
        if wasPlaying { pause() }
        guard let current = currentSample?.timestampMs else { return }
        let target = current + deltaMs
        if deltaMs > 0 {
            if let idx = samples.firstIndex(where: { $0.timestampMs >= target }) {
                currentIndex = idx
            } else {
                currentIndex = samples.count - 1
            }
        } else {
            if let idx = samples.lastIndex(where: { $0.timestampMs <= target }) {
                currentIndex = idx
            } else {
                currentIndex = 0
            }
        }
        isFinished = false
        if wasPlaying { play() }
    }

    func setSpeed(_ multiplier: Float) {
        let wasPlaying = isPlaying
        if wasPlaying { pause() }
        speedMultiplier = multiplier
        if wasPlaying { play() }
    }

    func stop() {
        pause()
        currentIndex = 0
        isFinished = false
    }
}
