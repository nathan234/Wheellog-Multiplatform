import SwiftUI

struct RidesView: View {
    @EnvironmentObject var wheelManager: WheelManager

    private let kmToMiles = 0.62137119223733

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
                Text(ride.startDate, style: .date)
                    .font(.headline)
                Text(ride.startDate, style: .time)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(formatDuration(ride.duration))
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(formatDistance(ride.distance))
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(formatSpeed(ride.maxSpeed) + " max")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            ShareLink(item: wheelManager.rideStore.fileURL(for: ride)) {
                Image(systemName: "square.and.arrow.up")
                    .font(.caption)
            }
            .buttonStyle(.borderless)
        }
        .padding(.vertical, 2)
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

    private func formatDistance(_ km: Double) -> String {
        if wheelManager.useMph {
            return String(format: "%.2f mi", km * kmToMiles)
        }
        return String(format: "%.2f km", km)
    }

    private func formatSpeed(_ kmh: Double) -> String {
        if wheelManager.useMph {
            return String(format: "%.0f mph", kmh * kmToMiles)
        }
        return String(format: "%.0f km/h", kmh)
    }
}

#Preview {
    NavigationStack {
        RidesView()
            .environmentObject(WheelManager())
    }
}
