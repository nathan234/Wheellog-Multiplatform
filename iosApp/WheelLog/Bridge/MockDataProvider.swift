import Foundation
import WheelLogCore

/// Provides simulated wheel data for testing without real Bluetooth.
/// Useful for iOS Simulator where BLE is not available.
class MockDataProvider: ObservableObject {
    @Published var isRunning = false

    private var timer: Timer?
    private var tick: Int = 0

    // Simulated wheel values
    private var speed: Double = 0.0
    private var battery: Int = 85
    private var temperature: Double = 25.0
    private var voltage: Double = 84.0
    private var current: Double = 0.0
    private var totalDistance: Double = 1523.5
    private var tripDistance: Double = 0.0

    // Callback to update wheel state
    var onStateUpdate: ((WheelStateBridge) -> Void)?

    func start() {
        guard !isRunning else { return }
        isRunning = true
        tick = 0

        // Simulate data at 10Hz (typical wheel update rate)
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.generateData()
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        isRunning = false
    }

    private func generateData() {
        tick += 1

        // Simulate a ride pattern
        let phase = Double(tick % 600) / 600.0  // 60 second cycle

        // Speed: accelerate, cruise, decelerate pattern
        if phase < 0.2 {
            // Accelerating
            speed = min(speed + 0.5, 25.0)
        } else if phase < 0.7 {
            // Cruising with slight variation
            speed = 22.0 + sin(Double(tick) * 0.1) * 3.0
        } else if phase < 0.9 {
            // Decelerating
            speed = max(speed - 0.3, 5.0)
        } else {
            // Stopped briefly
            speed = max(speed - 1.0, 0.0)
        }

        // Current based on acceleration
        if speed > 0 {
            current = speed * 0.8 + sin(Double(tick) * 0.05) * 2.0
        } else {
            current = 0
        }

        // Temperature rises slightly during ride
        temperature = 25.0 + (speed / 25.0) * 10.0 + sin(Double(tick) * 0.01) * 2.0

        // Voltage drops slightly under load
        voltage = 84.0 - (current * 0.05)

        // Battery drains slowly
        if tick % 100 == 0 && battery > 10 {
            battery -= 1
        }

        // Trip distance accumulates
        tripDistance += speed / 36000.0  // km per 0.1s tick

        // Create state bridge
        let state = WheelStateBridge(
            speed: speed,
            voltage: voltage,
            current: current,
            power: voltage * current,
            battery: Int32(battery),
            temperature: Int32(Int(temperature)),
            totalDistance: totalDistance + tripDistance,
            tripDistance: tripDistance,
            topSpeed: max(speed, 25.0),
            averageSpeed: 18.5,
            rideTime: Int32(tick / 10),
            model: "Mock Wheel S18",
            name: "Simulator",
            isConnected: true
        )

        onStateUpdate?(state)
    }
}

/// Bridge struct matching WheelState properties for SwiftUI
struct WheelStateBridge {
    let speed: Double
    let voltage: Double
    let current: Double
    let power: Double
    let battery: Int32
    let temperature: Int32
    let totalDistance: Double
    let tripDistance: Double
    let topSpeed: Double
    let averageSpeed: Double
    let rideTime: Int32
    let model: String
    let name: String
    let isConnected: Bool
}
