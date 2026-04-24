import Foundation

/// Where a ride row came from. Mirrors the Android `RideSource` enum so the
/// sqlite side + on-disk JSON stay semantically aligned.
enum RideSource: String, Codable {
    case ownLog = "OWN_LOG"
    case imported = "IMPORTED"
}

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
    let maxCurrent: Double
    let maxPower: Double
    let maxPwm: Double
    let consumptionWh: Double
    let consumptionWhPerKm: Double
    /// OWN_LOG for rides captured on this device; IMPORTED for rides loaded
    /// from a shared GPX. Decoded lenient-ly so rides persisted before this
    /// field existed default to .ownLog.
    let source: RideSource

    init(id: String, fileName: String, startDate: Date, endDate: Date,
         duration: TimeInterval, distance: Double, maxSpeed: Double,
         avgSpeed: Double, sampleCount: Int, fileSize: Int64,
         maxCurrent: Double = 0, maxPower: Double = 0, maxPwm: Double = 0,
         consumptionWh: Double = 0, consumptionWhPerKm: Double = 0,
         source: RideSource = .ownLog) {
        self.id = id
        self.fileName = fileName
        self.startDate = startDate
        self.endDate = endDate
        self.duration = duration
        self.distance = distance
        self.maxSpeed = maxSpeed
        self.avgSpeed = avgSpeed
        self.sampleCount = sampleCount
        self.fileSize = fileSize
        self.maxCurrent = maxCurrent
        self.maxPower = maxPower
        self.maxPwm = maxPwm
        self.consumptionWh = consumptionWh
        self.consumptionWhPerKm = consumptionWhPerKm
        self.source = source
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        fileName = try c.decode(String.self, forKey: .fileName)
        startDate = try c.decode(Date.self, forKey: .startDate)
        endDate = try c.decode(Date.self, forKey: .endDate)
        duration = try c.decode(TimeInterval.self, forKey: .duration)
        distance = try c.decode(Double.self, forKey: .distance)
        maxSpeed = try c.decode(Double.self, forKey: .maxSpeed)
        avgSpeed = try c.decode(Double.self, forKey: .avgSpeed)
        sampleCount = try c.decode(Int.self, forKey: .sampleCount)
        fileSize = try c.decode(Int64.self, forKey: .fileSize)
        maxCurrent = try c.decodeIfPresent(Double.self, forKey: .maxCurrent) ?? 0
        maxPower = try c.decodeIfPresent(Double.self, forKey: .maxPower) ?? 0
        maxPwm = try c.decodeIfPresent(Double.self, forKey: .maxPwm) ?? 0
        consumptionWh = try c.decodeIfPresent(Double.self, forKey: .consumptionWh) ?? 0
        consumptionWhPerKm = try c.decodeIfPresent(Double.self, forKey: .consumptionWhPerKm) ?? 0
        source = try c.decodeIfPresent(RideSource.self, forKey: .source) ?? .ownLog
    }
}

@MainActor
class RideStore: ObservableObject {

    @Published var rides: [RideMetadata] = []

    private static let metadataFileName = "metadata.json"

    nonisolated init() {}

    /// Must be called after init to load persisted rides.
    func initialize() {
        loadRides()
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
        for index in offsets.sorted().reversed() {
            rides.remove(at: index)
        }
        saveRides()
    }

    func removeRides(withIds ids: Set<String>) {
        for id in ids {
            if let idx = rides.firstIndex(where: { $0.id == id }) {
                let ride = rides[idx]
                let fileURL = Self.ridesDirectory().appendingPathComponent(ride.fileName)
                try? FileManager.default.removeItem(at: fileURL)
                rides.remove(at: idx)
            }
        }
        saveRides()
    }

    /// Dedup lookup for imported rides — returns the existing row with the
    /// same rideId (Identifiable `id`) if one is already persisted.
    func ride(withId id: String) -> RideMetadata? {
        rides.first { $0.id == id }
    }

    /// Insert or replace by rideId — used by import flows so re-importing the
    /// same shared GPX refreshes in place rather than duplicating.
    func upsertByRideId(_ ride: RideMetadata) {
        if let idx = rides.firstIndex(where: { $0.id == ride.id }) {
            let old = rides[idx]
            if old.fileName != ride.fileName {
                let oldURL = Self.ridesDirectory().appendingPathComponent(old.fileName)
                try? FileManager.default.removeItem(at: oldURL)
            }
            rides[idx] = ride
        } else {
            rides.insert(ride, at: 0)
        }
        rides.sort { $0.startDate > $1.startDate }
        saveRides()
    }

    func replaceRide(id: String, with newRides: [RideMetadata]) {
        if let idx = rides.firstIndex(where: { $0.id == id }) {
            let oldRide = rides[idx]
            let fileURL = Self.ridesDirectory().appendingPathComponent(oldRide.fileName)
            try? FileManager.default.removeItem(at: fileURL)
            rides.remove(at: idx)
        }
        for ride in newRides {
            rides.append(ride)
        }
        rides.sort { $0.startDate > $1.startDate }
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
