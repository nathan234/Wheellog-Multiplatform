import SwiftUI
import Charts
import WheelLogCore

struct TelemetryChartView: View {
    @EnvironmentObject var wheelManager: WheelManager

    @State private var showSpeed = true
    @State private var showGpsSpeed = false
    @State private var showCurrent = true
    @State private var showPower = false
    @State private var showTemperature = false
    @State private var selectedSample: TelemetrySample?

    private var chartSamples: [TelemetrySample] {
        if wheelManager.telemetryHistory.timeRange == .fiveMinutes {
            return wheelManager.telemetryBuffer.samples
        } else {
            return wheelManager.telemetryHistory.samples
        }
    }

    private var axisStride: Calendar.Component {
        switch wheelManager.telemetryHistory.timeRange {
        case .fiveMinutes: return .second
        case .oneHour: return .minute
        case .twentyFourHours: return .hour
        default: return .second
        }
    }

    private var axisStrideCount: Int {
        switch wheelManager.telemetryHistory.timeRange {
        case .fiveMinutes: return 10
        case .oneHour: return 5
        case .twentyFourHours: return 2
        default: return 10
        }
    }

    private var axisFormat: Date.FormatStyle {
        if wheelManager.telemetryHistory.timeRange == .fiveMinutes {
            return .dateTime.minute().second()
        } else {
            return .dateTime.hour().minute()
        }
    }

    private func displaySpeed(_ kmh: Double) -> Double {
        DisplayUtils.shared.convertSpeed(kmh: kmh, useMph: wheelManager.useMph)
    }

    private var speedUnit: String {
        DisplayUtils.shared.speedUnit(useMph: wheelManager.useMph)
    }

    private func displayTemp(_ celsius: Double) -> Double {
        DisplayUtils.shared.convertTemp(celsius: celsius, useFahrenheit: wheelManager.useFahrenheit)
    }

    private var tempUnit: String {
        DisplayUtils.shared.temperatureUnit(useFahrenheit: wheelManager.useFahrenheit)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Time range picker
                Picker("Range", selection: Binding(
                    get: { wheelManager.telemetryHistory.timeRange },
                    set: { wheelManager.telemetryHistory.setTimeRange($0) }
                )) {
                    Text("5m").tag(ChartTimeRange.fiveMinutes)
                    Text("1h").tag(ChartTimeRange.oneHour)
                    Text("24h").tag(ChartTimeRange.twentyFourHours)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                // Toggle chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ToggleChip(label: "Wheel", color: .blue, isOn: $showSpeed)
                        ToggleChip(label: "GPS", color: .cyan, isOn: $showGpsSpeed)
                        ToggleChip(label: "Current", color: .orange, isOn: $showCurrent)
                        ToggleChip(label: "Power", color: .green, isOn: $showPower)
                        ToggleChip(label: "Temp", color: .red, isOn: $showTemperature)
                    }
                    .padding(.horizontal)
                }

                if chartSamples.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "chart.xyaxis.line")
                            .font(.system(size: 48))
                            .foregroundColor(.secondary)
                        Text("Waiting for data...")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity, minHeight: 300)
                } else {
                    // Main telemetry chart
                    Chart {
                        if showSpeed {
                            ForEach(chartSamples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Speed", displaySpeed(sample.speed)),
                                    series: .value("Series", "Wheel")
                                )
                                .foregroundStyle(.blue)
                            }
                        }
                        if showGpsSpeed {
                            ForEach(chartSamples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("GPS", displaySpeed(sample.gpsSpeed)),
                                    series: .value("Series", "GPS")
                                )
                                .foregroundStyle(.cyan)
                            }
                        }
                        if showCurrent {
                            ForEach(chartSamples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Current", sample.current),
                                    series: .value("Series", "Current")
                                )
                                .foregroundStyle(.orange)
                            }
                        }
                        if showPower {
                            ForEach(chartSamples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Power", sample.power),
                                    series: .value("Series", "Power")
                                )
                                .foregroundStyle(.green)
                            }
                        }
                        if showTemperature {
                            ForEach(chartSamples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Temp", displayTemp(sample.temperature)),
                                    series: .value("Series", "Temp")
                                )
                                .foregroundStyle(.red)
                            }
                        }

                        if let selected = selectedSample {
                            RuleMark(x: .value("Time", selected.timestamp))
                                .foregroundStyle(.gray.opacity(0.5))
                                .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                                .annotation(position: mainChartAnnotationPosition(for: selected), spacing: 8) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        if showSpeed {
                                            HStack(spacing: 4) {
                                                Circle().fill(.blue).frame(width: 6, height: 6)
                                                Text(String(format: "%.1f %@", displaySpeed(selected.speed), speedUnit))
                                            }
                                        }
                                        if showGpsSpeed {
                                            HStack(spacing: 4) {
                                                Circle().fill(.cyan).frame(width: 6, height: 6)
                                                Text(String(format: "GPS %.1f %@", displaySpeed(selected.gpsSpeed), speedUnit))
                                            }
                                        }
                                        if showCurrent {
                                            HStack(spacing: 4) {
                                                Circle().fill(.orange).frame(width: 6, height: 6)
                                                Text(String(format: "%.1f A", selected.current))
                                            }
                                        }
                                        if showPower {
                                            HStack(spacing: 4) {
                                                Circle().fill(.green).frame(width: 6, height: 6)
                                                Text(String(format: "%.0f W", selected.power))
                                            }
                                        }
                                        if showTemperature {
                                            HStack(spacing: 4) {
                                                Circle().fill(.red).frame(width: 6, height: 6)
                                                Text(String(format: "%.0f%@", displayTemp(selected.temperature), tempUnit))
                                            }
                                        }
                                        Text(selected.timestamp, format: .dateTime.hour().minute().second())
                                            .foregroundColor(.secondary)
                                    }
                                    .font(.caption)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color(.systemBackground))
                                    .cornerRadius(6)
                                    .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
                                }
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: .stride(by: axisStride, count: axisStrideCount)) { _ in
                            AxisGridLine()
                            AxisValueLabel(format: axisFormat)
                        }
                    }
                    .chartYAxis {
                        AxisMarks { _ in
                            AxisGridLine()
                            AxisValueLabel()
                        }
                    }
                    .chartOverlay { proxy in
                        GeometryReader { geometry in
                            Rectangle()
                                .fill(Color.clear)
                                .contentShape(Rectangle())
                                .gesture(
                                    DragGesture(minimumDistance: 0)
                                        .onChanged { value in
                                            let originX = geometry[proxy.plotAreaFrame].origin.x
                                            let locationX = value.location.x - originX
                                            if let date: Date = proxy.value(atX: locationX) {
                                                selectedSample = nearestSample(to: date, in: chartSamples)
                                            }
                                        }
                                        .onEnded { _ in
                                            selectedSample = nil
                                        }
                                )
                        }
                    }
                    .frame(height: 250)
                    .padding(.horizontal)

                    // Voltage chart
                    VoltageChartView(samples: chartSamples, axisStride: axisStride, axisStrideCount: axisStrideCount, axisFormat: axisFormat)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Telemetry Chart")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func mainChartAnnotationPosition(for sample: TelemetrySample) -> AnnotationPosition {
        guard let first = chartSamples.first?.timestamp,
              let last = chartSamples.last?.timestamp else { return .top }
        let range = last.timeIntervalSince(first)
        guard range > 0 else { return .top }
        let position = sample.timestamp.timeIntervalSince(first) / range
        return position > 0.75 ? .topLeading : .topTrailing
    }
}

// MARK: - Shared Helpers

private func nearestSample(to date: Date, in samples: [TelemetrySample]) -> TelemetrySample? {
    samples.min(by: {
        abs($0.timestamp.timeIntervalSince(date)) < abs($1.timestamp.timeIntervalSince(date))
    })
}

private func annotationPosition(for sample: TelemetrySample, in samples: [TelemetrySample]) -> AnnotationPosition {
    guard let first = samples.first?.timestamp,
          let last = samples.last?.timestamp else { return .top }
    let range = last.timeIntervalSince(first)
    guard range > 0 else { return .top }
    let position = sample.timestamp.timeIntervalSince(first) / range
    return position > 0.75 ? .topLeading : .topTrailing
}

// MARK: - Voltage Chart with press-and-hold selection

struct VoltageChartView: View {
    let samples: [TelemetrySample]
    var axisStride: Calendar.Component = .second
    var axisStrideCount: Int = 10
    var axisFormat: Date.FormatStyle = .dateTime.minute().second()

    @State private var selectedSample: TelemetrySample?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Voltage")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.purple)
                .padding(.horizontal)

            Chart {
                ForEach(samples) { sample in
                    LineMark(
                        x: .value("Time", sample.timestamp),
                        y: .value("Voltage", sample.voltage)
                    )
                    .foregroundStyle(.purple)
                    .interpolationMethod(.catmullRom)
                }

                if let selected = selectedSample {
                    RuleMark(x: .value("Time", selected.timestamp))
                        .foregroundStyle(.gray.opacity(0.5))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                        .annotation(position: annotationPosition(for: selected, in: samples), spacing: 8) {
                            VStack(spacing: 2) {
                                Text(String(format: "%.2f V", selected.voltage))
                                    .font(.caption)
                                    .fontWeight(.bold)
                                Text(selected.timestamp, format: .dateTime.hour().minute().second())
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(.systemBackground))
                            .cornerRadius(6)
                            .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
                        }

                    PointMark(
                        x: .value("Time", selected.timestamp),
                        y: .value("Voltage", selected.voltage)
                    )
                    .foregroundStyle(.purple)
                    .symbolSize(60)
                }
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: axisStride, count: axisStrideCount)) { _ in
                    AxisGridLine()
                    AxisValueLabel(format: axisFormat)
                }
            }
            .chartYAxis {
                AxisMarks { _ in
                    AxisGridLine()
                    AxisValueLabel()
                }
            }
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(Color.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { value in
                                    let originX = geometry[proxy.plotAreaFrame].origin.x
                                    let locationX = value.location.x - originX
                                    if let date: Date = proxy.value(atX: locationX) {
                                        selectedSample = nearestSample(to: date, in: samples)
                                    }
                                }
                                .onEnded { _ in
                                    selectedSample = nil
                                }
                        )
                }
            }
            .frame(height: 200)
            .padding(.horizontal)
        }
    }
}

// MARK: - Toggle Chip

struct ToggleChip: View {
    let label: String
    let color: Color
    @Binding var isOn: Bool

    var body: some View {
        Button(action: { isOn.toggle() }) {
            Text(label)
                .font(.caption)
                .fontWeight(.medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isOn ? color.opacity(0.2) : Color(.systemGray5))
                .foregroundColor(isOn ? color : .secondary)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(isOn ? color : Color.clear, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        TelemetryChartView()
            .environmentObject(WheelManager())
    }
}
