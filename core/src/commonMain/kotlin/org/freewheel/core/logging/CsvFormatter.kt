package org.freewheel.core.logging

import org.freewheel.core.domain.TelemetryState

/**
 * Formats ride data as CSV rows matching the legacy WheelLog format.
 *
 * Column order (without GPS):
 * date,time,speed,voltage,phase_current,current,power,torque,pwm,
 * battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert
 *
 * With GPS, six columns are inserted after time:
 * latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance
 */
object CsvFormatter {

    private const val TELEMETRY_HEADER =
        "speed,voltage,phase_current,current,power,torque,pwm," +
        "battery_level,distance,totaldistance,system_temp,temp2,tilt,roll,mode,alert"

    private const val GPS_HEADER =
        "latitude,longitude,gps_speed,gps_alt,gps_heading,gps_distance,"

    /**
     * Returns the CSV header line.
     * @param includeGps true to include GPS columns between time and speed.
     */
    fun header(includeGps: Boolean): String {
        val gps = if (includeGps) GPS_HEADER else ""
        return "date,time,${gps}$TELEMETRY_HEADER"
    }

    /**
     * Returns a single CSV data row.
     *
     * @param dateTime Pre-formatted timestamp string as "yyyy-MM-dd,HH:mm:ss.SSS".
     * @param telemetry Current telemetry state.
     * @param modeStr Ride mode string (from WheelIdentity).
     * @param tripDistance Trip distance in meters (totalDistance - startTotalDistance).
     * @param gps Optional GPS location data. When non-null, GPS columns are included.
     */
    fun row(
        dateTime: String,
        telemetry: TelemetryState,
        modeStr: String = "",
        tripDistance: Int,
        gps: GpsLocation? = null,
        includeGps: Boolean = false
    ): String {
        val gpsStr = if (gps != null) {
            "${gps.latitude},${gps.longitude},${gps.speedKmh},${gps.altitude},${gps.bearing},${formatFixed(gps.cumulativeDistance, 0)},"
        } else if (includeGps) {
            ",,,,,,"
        } else ""

        return buildString {
            // date,time (dateTime contains the comma)
            append(dateTime)
            append(',')
            // GPS columns (empty string or data ending with comma)
            append(gpsStr)
            // Telemetry columns
            append(formatFixed(telemetry.speedKmh, 2))
            append(',')
            append(formatFixed(telemetry.voltageV, 2))
            append(',')
            append(formatFixed(telemetry.phaseCurrentA, 2))
            append(',')
            append(formatFixed(telemetry.currentA, 2))
            append(',')
            append(formatFixed(telemetry.powerW, 2))
            append(',')
            append(formatFixed(telemetry.torque, 2))
            append(',')
            append(formatFixed(telemetry.pwmPercent, 2))
            append(',')
            append(telemetry.batteryLevel)
            append(',')
            append(tripDistance)
            append(',')
            append(telemetry.totalDistance)
            append(',')
            append(telemetry.temperatureC)
            append(',')
            append(telemetry.temperature2C)
            append(',')
            append(formatFixed(telemetry.angle, 2))
            append(',')
            append(formatFixed(telemetry.roll, 2))
            append(',')
            append(modeStr)
            append(',')
            append(telemetry.alert)
        }
    }
}
