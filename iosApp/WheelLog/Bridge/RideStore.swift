import Foundation

struct RideMetadata: Codable, Identifiable {
    let id: String
    let fileName: String
    let startDate: Date
    let endDate: Date
    let duration: TimeInterval
    let distance: Double
    let maxSpeed: Double
    let avgSpeed: Double
    let sampleCount: Int
    let fileSize: Int64
}

@MainActor
class RideStore: ObservableObject {

    @Published var rides: [RideMetadata] = []

    private static let metadataFileName = "metadata.json"

    nonisolated init() {
        Task { @MainActor in
            loadRides()
        }
    }

    // MARK: - CRUD

    func addRide(_ ride: RideMetadata) {
        rides.insert(ride, at: 0)
        saveRides()
    }

    func deleteRide(at offsets: IndexSet) {
        let toDelete = offsets.map { rides[$0] }
        for ride in toDelete {
            let fileURL = Self.ridesDirectory().appendingPathComponent(ride.fileName)
            try? FileManager.default.removeItem(at: fileURL)
        }
        rides.remove(atOffsets: offsets)
        saveRides()
    }

    func fileURL(for ride: RideMetadata) -> URL {
        Self.ridesDirectory().appendingPathComponent(ride.fileName)
    }

    // MARK: - Persistence

    func loadRides() {
        let metaURL = Self.ridesDirectory().appendingPathComponent(Self.metadataFileName)
        guard let data = try? Data(contentsOf: metaURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        if let loaded = try? decoder.decode([RideMetadata].self, from: data) {
            rides = loaded.sorted { $0.startDate > $1.startDate }
        }
    }

    private func saveRides() {
        let ridesDir = Self.ridesDirectory()
        try? FileManager.default.createDirectory(at: ridesDir, withIntermediateDirectories: true)

        let metaURL = ridesDir.appendingPathComponent(Self.metadataFileName)
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(rides) {
            try? data.write(to: metaURL)
        }
    }

    // MARK: - Directory

    static func ridesDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("rides")
    }
}
