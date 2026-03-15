import SwiftUI
import Charts
import FreeWheelCore

// MARK: - Shared Chart Utilities

func nearestSample(to date: Date, in samples: [TelemetrySample]) -> TelemetrySample? {
    samples.min(by: {
        abs($0.timestamp.timeIntervalSince(date)) < abs($1.timestamp.timeIntervalSince(date))
    })
}

func chartAnnotationPosition(for sample: TelemetrySample, in samples: [TelemetrySample]) -> AnnotationPosition {
    guard let first = samples.first?.timestamp,
          let last = samples.last?.timestamp else { return .top }
    let range = last.timeIntervalSince(first)
    guard range > 0 else { return .top }
    let position = sample.timestamp.timeIntervalSince(first) / range
    return position > 0.75 ? .topLeading : .topTrailing
}

// MARK: - Chart Annotation Content

struct ChartAnnotationContent: View {
    let sample: TelemetrySample
    let visibleSeries: [(label: String, color: Color, value: String)]

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            ForEach(visibleSeries.indices, id: \.self) { index in
                let series = visibleSeries[index]
                HStack(spacing: 4) {
                    Circle().fill(series.color).frame(width: 6, height: 6)
                    Text(series.value)
                }
            }
            Text(sample.timestamp, format: .dateTime.hour().minute().second())
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

// MARK: - Chart Selection Overlay

func chartSelectionOverlay(
    proxy: ChartProxy,
    geometry: GeometryProxy,
    samples: [TelemetrySample],
    onSelect: @escaping (TelemetrySample?) -> Void
) -> some Gesture {
    DragGesture(minimumDistance: 0)
        .onChanged { value in
            let originX = geometry[proxy.plotAreaFrame].origin.x
            let locationX = value.location.x - originX
            if let date: Date = proxy.value(atX: locationX) {
                onSelect(nearestSample(to: date, in: samples))
            }
        }
        .onEnded { _ in
            onSelect(nil)
        }
}

// MARK: - Chart Zoom/Pan Helpers

func chartFullDomain(samples: [TelemetrySample]) -> TimeInterval {
    guard let first = samples.first?.timestamp,
          let last = samples.last?.timestamp else { return 1 }
    return max(last.timeIntervalSince(first), 1)
}

func isChartZoomed(visibleDomain: TimeInterval, samples: [TelemetrySample]) -> Bool {
    visibleDomain > 0 && visibleDomain < chartFullDomain(samples: samples) * 0.99
}

@available(iOS 17, *)
extension View {
    /// Adds horizontal scroll, pinch-to-zoom, and double-tap-to-reset to a Chart.
    func zoomableChart(
        samples: [TelemetrySample],
        visibleDomain: Binding<TimeInterval>,
        baseDomain: Binding<TimeInterval>
    ) -> some View {
        let fullDomain = chartFullDomain(samples: samples)
        return self
            .chartScrollableAxes(.horizontal)
            .chartXVisibleDomain(length: max(visibleDomain.wrappedValue, 1))
            .gesture(
                MagnifyGesture()
                    .onChanged { value in
                        let base = baseDomain.wrappedValue > 0 ? baseDomain.wrappedValue : fullDomain
                        let newDomain = base / value.magnification
                        let minDomain = max(10, fullDomain / 50)
                        visibleDomain.wrappedValue = min(max(newDomain, minDomain), fullDomain)
                    }
                    .onEnded { _ in
                        baseDomain.wrappedValue = visibleDomain.wrappedValue
                    }
            )
            .onTapGesture(count: 2) {
                withAnimation {
                    visibleDomain.wrappedValue = fullDomain
                    baseDomain.wrappedValue = fullDomain
                }
            }
    }
}

/// "Fit All" reset button shown when chart is zoomed in.
struct ChartResetButton: View {
    let visibleDomain: TimeInterval
    let samples: [TelemetrySample]
    let onReset: () -> Void

    var body: some View {
        if isChartZoomed(visibleDomain: visibleDomain, samples: samples) {
            Button(action: {
                withAnimation { onReset() }
            }) {
                Text("Fit All")
                    .font(.caption2)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.ultraThinMaterial)
                    .cornerRadius(12)
            }
        }
    }
}
