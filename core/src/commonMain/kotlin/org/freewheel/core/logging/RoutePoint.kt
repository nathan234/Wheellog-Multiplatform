package org.freewheel.core.logging

/**
 * A single GPS coordinate from a parsed ride CSV, used for map route display.
 *
 * This is the read-back counterpart of [GpsLocation] (which is used at write time).
 *
 * @property timestampMs Epoch milliseconds.
 * @property latitude Latitude in degrees.
 * @property longitude Longitude in degrees.
 * @property altitude Altitude in meters.
 * @property bearing Heading in degrees (0–360).
 * @property speedKmh Wheel-reported speed in km/h (for speed-colored polylines).
 * @property gpsSpeedKmh GPS-reported speed in km/h.
 */
data class RoutePoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Double,
    val speedKmh: Double,
    val gpsSpeedKmh: Double
)
