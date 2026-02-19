import Foundation
import WheelLogCore

typealias ChartTimeRange = WheelLogCore.ChartTimeRange

/// Swift bridge for KMP TelemetryHistory.
/// Provides persistent, downsampled 24-hour telemetry storage.
@MainActor
class TelemetryHistoryBridge: ObservableObject {

    @Published var samples: [TelemetrySample] = []
    @Published var timeRange: ChartTimeRange = .fiveMinutes

    private var kmpHistory: TelemetryHistory?
    private let fileIO = PlatformTelemetryFileIO()

    nonisolated init() {
        // kmpHistory is initialized on loadForWheel
    }

    /// Load history for a specific wheel address.
    /// Call on connect; creates or loads the CSV file.
    func loadForWheel(address: String) {
        // Save any existing history first
        kmpHistory?.save()

        let sanitized = address
            .replacingOccurrences(of: ":", with: "_")
            .replacingOccurrences(of: "/", with: "_")

        let supportDir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let telemetryDir = supportDir.appendingPathComponent("telemetry", isDirectory: true)

        // Ensure directory exists
        try? FileManager.default.createDirectory(at: telemetryDir, withIntermediateDirectories: true)

        let filePath = telemetryDir.appendingPathComponent("\(sanitized).csv").path
        let history = TelemetryHistory(fileIO: fileIO)
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        history.loadForWheel(path: filePath, nowMs: now)
        kmpHistory = history
        syncSamples()
    }

    /// Add a telemetry sample (same data as TelemetryBuffer).
    func addSample(
        speedKmh: Double,
        voltage: Double,
        current: Double,
        power: Double,
        temperature: Int,
        battery: Int,
        pwmPercent: Double = 0,
        gpsSpeedKmh: Double = 0
    ) {
        guard let history = kmpHistory else { return }
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
        let _ = history.addSample(sample: sample)
        // Sync published samples if viewing non-5m range
        if timeRange != .fiveMinutes {
            syncSamples()
        }
    }

    /// Change the displayed time range and refresh samples.
    func setTimeRange(_ range: ChartTimeRange) {
        timeRange = range
        syncSamples()
    }

    /// Save history to disk. Call on disconnect and background.
    func save() {
        kmpHistory?.save()
    }

    /// Clear in-memory data (does not delete file).
    func clear() {
        kmpHistory?.clear()
        samples = []
    }

    /// Get statistics for a metric in the current time range.
    func statsForRange(metric: MetricType) -> MetricStats {
        return kmpHistory?.statsForRange(metric: metric, range: timeRange)
            ?? MetricStats(min: 0, max: 0, avg: 0)
    }

    private func syncSamples() {
        guard let history = kmpHistory else {
            samples = []
            return
        }
        let kmpSamples = history.samplesForRange(range: timeRange)
        samples = kmpSamples.compactMap { $0 as? WheelLogCore.TelemetrySample }
    }
}
