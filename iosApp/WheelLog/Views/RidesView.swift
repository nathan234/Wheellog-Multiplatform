import SwiftUI
import WheelLogCore

struct RidesView: View {
    @EnvironmentObject var wheelManager: WheelManager

    var body: some View {
        Group {
            if wheelManager.rideStore.rides.isEmpty {
                emptyState
            } else {
                rideList
            }
        }
        .navigationTitle("Rides")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if wheelManager.connectionState.isConnected {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: toggleLogging) {
                        Image(systemName: wheelManager.isLogging ? "stop.circle.fill" : "record.circle")
                            .foregroundColor(wheelManager.isLogging ? .red : .primary)
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "road.lanes")
                .font(.system(size: 60))
                .foregroundColor(.secondary)
            Text("No Rides Recorded")
                .font(.title2)
                .fontWeight(.medium)
            Text("Connect to your wheel and start logging")
                .font(.body)
                .foregroundColor(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var rideList: some View {
        List {
            ForEach(wheelManager.rideStore.rides) { ride in
                rideRow(ride)
            }
            .onDelete { offsets in
                wheelManager.rideStore.deleteRide(at: offsets)
            }
        }
        .listStyle(.insetGrouped)
    }

    private func rideRow(_ ride: RideMetadata) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                // Friendly date title
                Text(friendlyDate(ride.startDate))
                    .font(.headline)

                // Line 1: Duration + Distance
                Text("\(formatDuration(ride.duration))  |  \(DisplayUtils.shared.formatDistance(km: ride.distance, useMph: wheelManager.useMph, decimals: 2))")
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                // Line 2: Max speed + Avg speed
                Text("\(DisplayUtils.shared.formatSpeed(kmh: ride.maxSpeed, useMph: wheelManager.useMph, decimals: 0)) max  |  \(DisplayUtils.shared.formatSpeed(kmh: ride.avgSpeed, useMph: wheelManager.useMph, decimals: 0)) avg")
                    .font(.caption)
                    .foregroundColor(.secondary)

                // Line 3: Power + Energy (if data exists)
                if ride.maxPower > 0 || ride.consumptionWhPerKm > 0 {
                    let parts = powerEnergyParts(ride)
                    Text(parts)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            ShareLink(item: wheelManager.rideStore.fileURL(for: ride)) {
                Image(systemName: "square.and.arrow.up")
                    .font(.caption)
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 2)
    }

    private func powerEnergyParts(_ ride: RideMetadata) -> String {
        var parts: [String] = []
        if ride.maxPower > 0 {
            parts.append("\(Int(ride.maxPower)) W max")
        }
        if ride.consumptionWhPerKm > 0 {
            if wheelManager.useMph {
                let whPerMi = ride.consumptionWhPerKm / ByteUtils.shared.KM_TO_MILES_MULTIPLIER
                parts.append(String(format: "%.1f Wh/mi", whPerMi))
            } else {
                parts.append(String(format: "%.1f Wh/km", ride.consumptionWhPerKm))
            }
        }
        return parts.joined(separator: "  |  ")
    }

    private func toggleLogging() {
        if wheelManager.isLogging {
            wheelManager.stopLogging()
        } else {
            wheelManager.startLogging()
        }
    }

    // MARK: - Formatting

    private func formatDuration(_ seconds: TimeInterval) -> String {
        let hours = Int(seconds) / 3600
        let minutes = (Int(seconds) % 3600) / 60
        let secs = Int(seconds) % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, secs)
        }
        return String(format: "%d:%02d", minutes, secs)
    }


    private func friendlyDate(_ date: Date) -> String {
        let calendar = Calendar.current
        let now = Date()

        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "h:mm a"
        let timeStr = timeFormatter.string(from: date)

        if calendar.isDateInToday(date) {
            return "Today, \(timeStr)"
        } else if calendar.isDateInYesterday(date) {
            return "Yesterday, \(timeStr)"
        } else if calendar.component(.year, from: date) == calendar.component(.year, from: now) {
            let dayFormatter = DateFormatter()
            dayFormatter.dateFormat = "EEE, MMM d"
            return "\(dayFormatter.string(from: date)), \(timeStr)"
        } else {
            let dayFormatter = DateFormatter()
            dayFormatter.dateFormat = "MMM d, yyyy"
            return "\(dayFormatter.string(from: date)), \(timeStr)"
        }
    }
}

#Preview {
    NavigationStack {
        RidesView()
            .environmentObject(WheelManager())
    }
}
