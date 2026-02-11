package com.cooper.wheellog.core.domain

/**
 * Immutable data class representing the current state of an electric unicycle.
 * All values use internal units (typically 1/100 for precision).
 *
 * Unit conventions:
 * - Speed: 1/100 km/h (e.g., 2500 = 25.00 km/h)
 * - Voltage: 1/100 V (e.g., 8400 = 84.00 V)
 * - Current: 1/100 A (e.g., 1500 = 15.00 A)
 * - Power: 1/100 W (e.g., 150000 = 1500.00 W)
 * - Temperature: 1/100 °C (e.g., 3500 = 35.00 °C)
 * - Distance: meters
 */
data class WheelState(
    // Core telemetry
    val speed: Int = 0,
    val voltage: Int = 0,
    val current: Int = 0,
    val phaseCurrent: Int = 0,
    val power: Int = 0,
    val temperature: Int = 0,
    val temperature2: Int = 0,
    val batteryLevel: Int = 0,

    // Distance tracking
    val totalDistance: Long = 0,
    val wheelDistance: Long = 0,

    // PWM and output
    val output: Int = 0,
    val calculatedPwm: Double = 0.0,

    // Orientation
    val angle: Double = 0.0,
    val roll: Double = 0.0,

    // Motor and performance
    val torque: Double = 0.0,
    val motorPower: Double = 0.0,
    val cpuTemp: Int = 0,
    val imuTemp: Int = 0,
    val cpuLoad: Int = 0,

    // Limits
    val speedLimit: Double = 0.0,
    val currentLimit: Double = 0.0,

    // Status flags
    val fanStatus: Int = 0,
    val chargingStatus: Int = 0,
    val wheelAlarm: Boolean = false,

    // Wheel identification
    val wheelType: WheelType = WheelType.Unknown,
    val name: String = "",
    val model: String = "",
    val modeStr: String = "",
    val version: String = "",
    val serialNumber: String = "",
    val btName: String = "",

    // BMS data
    val bms1: SmartBms? = null,
    val bms2: SmartBms? = null,

    // Wheel settings reported via BLE
    val inMiles: Boolean = false,
    val pedalsMode: Int = -1,       // 0=Hard, 1=Medium, 2=Soft (-1=unknown)
    val speedAlarms: Int = -1,      // 0=alarms on, 1=off level 1, 2=off level 2
    val rollAngle: Int = -1,        // 0=Low, 1=Medium, 2=High
    val tiltBackSpeed: Int = 0,     // km/h (0-99)
    val lightMode: Int = -1,        // 0=Off, 1=On, 2=Strobe
    val ledMode: Int = -1,          // 0-9 LED pattern

    // Error tracking
    val error: String = "",
    val alert: String = "",

    // Timestamp of last update
    val timestamp: Long = 0
) {
    // Computed properties for display

    /** Speed in km/h */
    val speedKmh: Double get() = speed / 100.0

    /** Speed in mph */
    val speedMph: Double get() = speedKmh * KM_TO_MILES

    /** Voltage in V */
    val voltageV: Double get() = voltage / 100.0

    /** Current in A */
    val currentA: Double get() = current / 100.0

    /** Phase current in A */
    val phaseCurrentA: Double get() = phaseCurrent / 100.0

    /** Power in W */
    val powerW: Double get() = power / 100.0

    /** Temperature in °C */
    val temperatureC: Int get() = temperature / 100

    /** Temperature in °F */
    val temperatureF: Double get() = temperatureC * 9.0 / 5.0 + 32

    /** Secondary temperature in °C */
    val temperature2C: Int get() = temperature2 / 100

    /** Total distance in km */
    val totalDistanceKm: Double get() = totalDistance / 1000.0

    /** Wheel distance in km */
    val wheelDistanceKm: Double get() = wheelDistance / 1000.0

    /** PWM as percentage (0-100) */
    val pwmPercent: Double get() = calculatedPwm * 100.0

    /** Output as percentage */
    val outputPercent: Int get() = output / 100

    companion object {
        const val KM_TO_MILES = 0.62137119223733
    }
}
