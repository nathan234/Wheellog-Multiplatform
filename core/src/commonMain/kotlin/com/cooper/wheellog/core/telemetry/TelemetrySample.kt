package com.cooper.wheellog.core.telemetry

/**
 * A single telemetry snapshot captured at a point in time.
 * Shared across Android and iOS via KMP.
 */
data class TelemetrySample(
    val timestampMs: Long,
    val speedKmh: Double,
    val voltageV: Double,
    val currentA: Double,
    val powerW: Double,
    val temperatureC: Double,
    val batteryPercent: Double,
    val pwmPercent: Double,
    val gpsSpeedKmh: Double = 0.0
)
