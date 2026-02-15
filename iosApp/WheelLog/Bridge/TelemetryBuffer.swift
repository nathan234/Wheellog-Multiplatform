import Foundation
import WheelLogCore

/// Module-level typealias so other Swift files can use `TelemetrySample`
/// without importing WheelLogCore directly.
typealias TelemetrySample = WheelLogCore.TelemetrySample
typealias MetricType = WheelLogCore.MetricType

/// Swift wrapper around the KMP TelemetryBuffer.
/// Exposes samples as a Published array for SwiftUI observation.
@MainActor
class TelemetryBuffer: ObservableObject {

    @Published var samples: [TelemetrySample] = []

    private let kmpBuffer = WheelLogCore.TelemetryBuffer(sampleIntervalMs: 500, maxAgeMs: 60_000)

    nonisolated init() {}

    func addSampleIfNeeded(
        speedKmh: Double,
        voltage: Double,
        current: Double,
        power: Double,
        temperature: Int,
        battery: Int,
        pwmPercent: Double = 0,
        gpsSpeedKmh: Double = 0
    ) {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let sample = WheelLogCore.TelemetrySample(
            timestampMs: now,
            speedKmh: speedKmh,
            voltageV: voltage,
            currentA: current,
            powerW: power,
            temperatureC: Double(temperature),
            batteryPercent: Double(battery),
            pwmPercent: pwmPercent,
            gpsSpeedKmh: gpsSpeedKmh
        )
        if kmpBuffer.addSampleIfNeeded(sample: sample) {
            // Convert KMP samples to Swift array for SwiftUI
            syncSamples()
        }
    }

    func clear() {
        kmpBuffer.clear()
        samples.removeAll()
    }

    /// Access the underlying KMP buffer for stats/values queries.
    var buffer: WheelLogCore.TelemetryBuffer { kmpBuffer }

    private func syncSamples() {
        samples = kmpBuffer.samples.compactMap { $0 as? WheelLogCore.TelemetrySample }
    }
}

/// Convenience extension to bridge KMP TelemetrySample for SwiftUI Identifiable conformance.
extension WheelLogCore.TelemetrySample: @retroactive Identifiable {
    public var id: Int64 { timestampMs }

    // Convenience accessors matching old Swift field names
    var timestamp: Date { Date(timeIntervalSince1970: Double(timestampMs) / 1000.0) }
    var speed: Double { speedKmh }
    var voltage: Double { voltageV }
    var current: Double { currentA }
    var power: Double { powerW }
    var temperature: Double { temperatureC }
    var battery: Double { batteryPercent }
}
