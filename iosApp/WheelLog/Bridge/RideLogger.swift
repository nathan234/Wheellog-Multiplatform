import Foundation
import CoreLocation

enum LoggingState {
    case idle
    case logging(startDate: Date, filePath: URL)
}

@MainActor
class RideLogger: ObservableObject {

    @Published private(set) var state: LoggingState = .idle

    var isLogging: Bool {
        if case .logging = state { return true }
        return false
    }

    private var fileHandle: FileHandle?
    private var pollCounter: Int = 0
    private let writeInterval: Int = 20  // Write every 20th poll tick (1Hz at 20Hz poll)

    // In-memory tracking for metadata
    private var maxSpeed: Double = 0
    private var sampleCount: Int = 0
    private var startDistance: Double = 0
    private var totalSpeed: Double = 0
    private var maxCurrent: Double = 0
    private var maxPower: Double = 0
    private var maxPwm: Double = 0
    private var totalPower: Double = 0

    nonisolated init() {}

    // MARK: - Start/Stop

    func startLogging(includeGPS: Bool) -> Bool {
        let ridesDir = Self.ridesDirectory()

        // Create rides directory if needed
        do {
            try FileManager.default.createDirectory(at: ridesDir, withIntermediateDirectories: true)
        } catch {
            print("Failed to create rides directory: \(error)")
            return false
        }

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy_MM_dd_HH_mm_ss"
        let fileName = "\(formatter.string(from: Date())).csv"
        let filePath = ridesDir.appendingPathComponent(fileName)

        // Write CSV header
        var header = "date,time"
        if includeGPS {
            header += ",latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance"
        }
        header += ",speed,voltage,phase_current,current,power,torque,pwm,battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert\n"

        guard FileManager.default.createFile(atPath: filePath.path, contents: header.data(using: .utf8)) else {
            print("Failed to create log file")
            return false
        }

        do {
            fileHandle = try FileHandle(forWritingTo: filePath)
            fileHandle?.seekToEndOfFile()
        } catch {
            print("Failed to open file handle: \(error)")
            return false
        }

        maxSpeed = 0
        sampleCount = 0
        startDistance = 0
        totalSpeed = 0
        maxCurrent = 0
        maxPower = 0
        maxPwm = 0
        totalPower = 0
        pollCounter = 0

        state = .logging(startDate: Date(), filePath: filePath)
        return true
    }

    func stopLogging(currentDistance: Double) -> RideMetadata? {
        guard case .logging(let startDate, let filePath) = state else { return nil }

        fileHandle?.closeFile()
        fileHandle = nil
        state = .idle

        let endDate = Date()
        let duration = endDate.timeIntervalSince(startDate)
        let distance = currentDistance - startDistance
        let avgSpeed = sampleCount > 0 ? totalSpeed / Double(sampleCount) : 0

        // Energy consumption
        let avgPowerW = sampleCount > 0 ? totalPower / Double(sampleCount) : 0
        let consumptionWh = duration > 0 ? avgPowerW * duration / 3600.0 : 0
        let distanceMeters = distance * 1000.0
        let consumptionWhPerKm = distanceMeters > 0 ? consumptionWh * 1000.0 / distanceMeters : 0

        // Get file size
        let fileSize: Int64
        if let attrs = try? FileManager.default.attributesOfItem(atPath: filePath.path),
           let size = attrs[.size] as? Int64 {
            fileSize = size
        } else {
            fileSize = 0
        }

        return RideMetadata(
            id: UUID().uuidString,
            fileName: filePath.lastPathComponent,
            startDate: startDate,
            endDate: endDate,
            duration: duration,
            distance: distance,
            maxSpeed: maxSpeed,
            avgSpeed: avgSpeed,
            sampleCount: sampleCount,
            fileSize: fileSize,
            maxCurrent: maxCurrent,
            maxPower: maxPower,
            maxPwm: maxPwm,
            consumptionWh: consumptionWh,
            consumptionWhPerKm: consumptionWhPerKm
        )
    }

    // MARK: - Write Sample

    struct SampleData {
        let speedKmh: Double
        let voltage: Double
        let current: Double
        let power: Double
        let pwm: Double
        let batteryLevel: Int
        let wheelDistanceKm: Double
        let totalDistanceKm: Double
        let temperature: Int
    }

    func writeSampleIfThrottled(data: SampleData, location: CLLocation?, includeGPS: Bool) {
        pollCounter += 1
        guard pollCounter >= writeInterval else { return }
        pollCounter = 0

        guard let handle = fileHandle else { return }

        // Track metadata
        if sampleCount == 0 {
            startDistance = data.totalDistanceKm
        }
        maxSpeed = max(maxSpeed, data.speedKmh)
        totalSpeed += data.speedKmh
        maxCurrent = max(maxCurrent, abs(data.current))
        let absPower = abs(data.power)
        maxPower = max(maxPower, absPower)
        maxPwm = max(maxPwm, data.pwm)
        totalPower += absPower
        sampleCount += 1

        let now = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let timeFormatter = DateFormatter()
        timeFormatter.dateFormat = "HH:mm:ss.SSS"

        var row = "\(dateFormatter.string(from: now)),\(timeFormatter.string(from: now))"

        if includeGPS {
            if let loc = location {
                row += String(format: ",%.6f,%.6f,%.1f,%.1f,%.1f,0",
                              loc.coordinate.latitude,
                              loc.coordinate.longitude,
                              loc.speed >= 0 ? loc.speed * 3.6 : 0,
                              loc.altitude,
                              loc.course >= 0 ? loc.course : 0)
            } else {
                row += ",,,,,,"
            }
        }

        row += String(format: ",%.2f,%.2f,%.2f,%.2f,%.0f,0,%.1f,%d,%.4f,%.4f,%d,0,0,0,0,",
                       data.speedKmh,
                       data.voltage,
                       data.current,
                       data.current,
                       data.power,
                       data.pwm,
                       data.batteryLevel,
                       data.wheelDistanceKm,
                       data.totalDistanceKm,
                       data.temperature)
        row += "\n"

        if let lineData = row.data(using: .utf8) {
            handle.write(lineData)
        }
    }

    // MARK: - Directory

    static func ridesDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("rides")
    }
}
