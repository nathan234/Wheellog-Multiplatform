import Foundation

struct TelemetrySample: Identifiable {
    let id = UUID()
    let timestamp: Date
    let speed: Double
    let voltage: Double
    let current: Double
    let power: Double
    let temperature: Double
    let battery: Double
}

@MainActor
class TelemetryBuffer: ObservableObject {

    @Published var samples: [TelemetrySample] = []

    private var lastSampleTime: Date?
    private let sampleInterval: TimeInterval = 0.5  // 2Hz
    private let maxAge: TimeInterval = 60.0

    nonisolated init() {}

    func addSampleIfNeeded(speedKmh: Double, voltage: Double, current: Double, power: Double, temperature: Int, battery: Int) {
        let now = Date()
        if let last = lastSampleTime, now.timeIntervalSince(last) < sampleInterval {
            return
        }
        lastSampleTime = now

        let sample = TelemetrySample(
            timestamp: now,
            speed: speedKmh,
            voltage: voltage,
            current: current,
            power: power,
            temperature: Double(temperature),
            battery: Double(battery)
        )

        samples.append(sample)

        // Trim samples older than 60s
        let cutoff = now.addingTimeInterval(-maxAge)
        samples.removeAll { $0.timestamp < cutoff }
    }

    func clear() {
        samples.removeAll()
        lastSampleTime = nil
    }
}
