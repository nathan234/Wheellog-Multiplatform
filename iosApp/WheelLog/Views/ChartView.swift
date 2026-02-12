import SwiftUI
import Charts

struct TelemetryChartView: View {
    @EnvironmentObject var wheelManager: WheelManager

    @State private var showSpeed = true
    @State private var showCurrent = true
    @State private var showPower = false
    @State private var showTemperature = false

    var body: some View {
        VStack(spacing: 12) {
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

            // Chart
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
                .frame(maxWidth: .infinity)
            } else {
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
                .frame(maxHeight: .infinity)
                .padding(.horizontal)
            }
        }
        .padding(.vertical)
        .navigationTitle("Telemetry Chart")
        .navigationBarTitleDisplayMode(.inline)
    }
}

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
