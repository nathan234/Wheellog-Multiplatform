import SwiftUI
import Charts

struct TelemetryChartView: View {
    @EnvironmentObject var wheelManager: WheelManager

    @State private var showSpeed = true
    @State private var showCurrent = true
    @State private var showPower = false
    @State private var showTemperature = false
    @State private var selectedSample: TelemetrySample?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Toggle chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ToggleChip(label: "Speed", color: .blue, isOn: $showSpeed)
                        ToggleChip(label: "Current", color: .orange, isOn: $showCurrent)
                        ToggleChip(label: "Power", color: .green, isOn: $showPower)
                        ToggleChip(label: "Temp", color: .red, isOn: $showTemperature)
                    }
                    .padding(.horizontal)
                }

                if wheelManager.telemetryBuffer.samples.isEmpty {
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
                            ForEach(wheelManager.telemetryBuffer.samples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Speed", sample.speed),
                                    series: .value("Series", "Speed")
                                )
                                .foregroundStyle(.blue)
                            }
                        }
                        if showCurrent {
                            ForEach(wheelManager.telemetryBuffer.samples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Current", sample.current),
                                    series: .value("Series", "Current")
                                )
                                .foregroundStyle(.orange)
                            }
                        }
                        if showPower {
                            ForEach(wheelManager.telemetryBuffer.samples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Power", sample.power),
                                    series: .value("Series", "Power")
                                )
                                .foregroundStyle(.green)
                            }
                        }
                        if showTemperature {
                            ForEach(wheelManager.telemetryBuffer.samples) { sample in
                                LineMark(
                                    x: .value("Time", sample.timestamp),
                                    y: .value("Temp", sample.temperature),
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
                                                Text(String(format: "%.1f km/h", selected.speed))
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
                                                Text(String(format: "%.0f°C", selected.temperature))
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
                        AxisMarks(values: .stride(by: .second, count: 10)) { _ in
                            AxisGridLine()
                            AxisValueLabel(format: .dateTime.minute().second())
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
                                                selectedSample = nearestSample(to: date, in: wheelManager.telemetryBuffer.samples)
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
                    VoltageChartView(samples: wheelManager.telemetryBuffer.samples)
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Telemetry Chart")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func mainChartAnnotationPosition(for sample: TelemetrySample) -> AnnotationPosition {
        guard let first = wheelManager.telemetryBuffer.samples.first?.timestamp,
              let last = wheelManager.telemetryBuffer.samples.last?.timestamp else { return .top }
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
                AxisMarks(values: .stride(by: .second, count: 10)) { _ in
                    AxisGridLine()
                    AxisValueLabel(format: .dateTime.minute().second())
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
