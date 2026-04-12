package org.freewheel.core.logging

import org.freewheel.core.telemetry.TelemetrySample
import org.freewheel.core.utils.Logger
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Parses ride CSV files (written by [CsvFormatter]) into [TelemetrySample] lists.
 * Handles both GPS and non-GPS column layouts by reading the header line.
 */
object CsvParser {

    private const val MAX_CHART_SAMPLES = 3600

    /**
     * Parse CSV content into a list of [TelemetrySample].
     * Long rides (> [MAX_CHART_SAMPLES]) are downsampled.
     *
     * @param csvContent Full CSV file content as a string.
     * @return Parsed samples, possibly downsampled. Empty list if parsing fails.
     */
    fun parse(csvContent: String): List<TelemetrySample> {
        val lines = csvContent.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()

        val headerLine = lines.next()
        val headers = headerLine.split(",")
        val colIndex = HashMap<String, Int>(headers.size)
        headers.forEachIndexed { i, name -> colIndex[name.trim()] = i }

        val dateIdx = colIndex[CsvColumns.DATE] ?: return emptyList()
        val timeIdx = colIndex[CsvColumns.TIME] ?: return emptyList()
        val speedIdx = colIndex[CsvColumns.SPEED] ?: return emptyList()
        val voltageIdx = colIndex[CsvColumns.VOLTAGE]
        val currentIdx = colIndex[CsvColumns.CURRENT]
        val powerIdx = colIndex[CsvColumns.POWER]
        val tempIdx = colIndex[CsvColumns.SYSTEM_TEMP]
        val batteryIdx = colIndex[CsvColumns.BATTERY_LEVEL]
        val pwmIdx = colIndex[CsvColumns.PWM]
        val gpsSpeedIdx = colIndex[CsvColumns.GPS_SPEED]

        val tz = TimeZone.currentSystemDefault()
        val samples = mutableListOf<TelemetrySample>()

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val cols = line.split(",")
            if (cols.size <= speedIdx) continue

            try {
                val timestampMs = parseTimestamp(cols[dateIdx], cols[timeIdx], tz) ?: continue

                samples.add(TelemetrySample(
                    timestampMs = timestampMs,
                    speedKmh = cols[speedIdx].toDoubleOrNull() ?: 0.0,
                    voltageV = voltageIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    currentA = currentIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    powerW = powerIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    temperatureC = tempIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    batteryPercent = batteryIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    pwmPercent = pwmIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    gpsSpeedKmh = gpsSpeedIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                ))
            } catch (e: Exception) {
                Logger.w("CsvParser", "Skipping malformed row: ${e.message}")
            }
        }

        return downsample(samples)
    }

    /**
     * Parse CSV content into a list of [RoutePoint] for map display.
     * Only returns points from GPS-enabled CSVs with valid (non-zero) coordinates.
     * Long routes (> [MAX_CHART_SAMPLES]) are downsampled.
     *
     * @param csvContent Full CSV file content as a string.
     * @return Parsed route points. Empty list if no GPS columns or parsing fails.
     */
    fun parseRoute(csvContent: String): List<RoutePoint> {
        val lines = csvContent.lineSequence().iterator()
        if (!lines.hasNext()) return emptyList()

        val headerLine = lines.next()
        val headers = headerLine.split(",")
        val colIndex = HashMap<String, Int>(headers.size)
        headers.forEachIndexed { i, name -> colIndex[name.trim()] = i }

        val dateIdx = colIndex[CsvColumns.DATE] ?: return emptyList()
        val timeIdx = colIndex[CsvColumns.TIME] ?: return emptyList()
        val latIdx = colIndex[CsvColumns.LATITUDE] ?: return emptyList()
        val lonIdx = colIndex[CsvColumns.LONGITUDE] ?: return emptyList()
        val speedIdx = colIndex[CsvColumns.SPEED] ?: return emptyList()
        val gpsSpeedIdx = colIndex[CsvColumns.GPS_SPEED]
        val altIdx = colIndex[CsvColumns.GPS_ALT]
        val headingIdx = colIndex[CsvColumns.GPS_HEADING]

        val tz = TimeZone.currentSystemDefault()
        val points = mutableListOf<RoutePoint>()

        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val cols = line.split(",")

            try {
                val lat = cols.getOrNull(latIdx)?.toDoubleOrNull() ?: continue
                val lon = cols.getOrNull(lonIdx)?.toDoubleOrNull() ?: continue
                if (lat == 0.0 && lon == 0.0) continue

                val timestampMs = parseTimestamp(cols[dateIdx], cols[timeIdx], tz) ?: continue

                points.add(RoutePoint(
                    timestampMs = timestampMs,
                    latitude = lat,
                    longitude = lon,
                    altitude = altIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    bearing = headingIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                    speedKmh = cols.getOrNull(speedIdx)?.toDoubleOrNull() ?: 0.0,
                    gpsSpeedKmh = gpsSpeedIdx?.let { cols.getOrNull(it)?.toDoubleOrNull() } ?: 0.0,
                ))
            } catch (e: Exception) {
                Logger.w("CsvParser", "Skipping malformed route row: ${e.message}")
            }
        }

        return downsample(points)
    }

    private fun <T> downsample(items: List<T>): List<T> {
        if (items.size <= MAX_CHART_SAMPLES) return items
        val step = items.size / MAX_CHART_SAMPLES
        return items.filterIndexed { index, _ -> index % step == 0 }
    }

    /**
     * Parse "yyyy-MM-dd" + "HH:mm:ss.SSS" into epoch milliseconds.
     */
    private fun parseTimestamp(dateStr: String, timeStr: String, tz: TimeZone): Long? {
        // dateStr = "yyyy-MM-dd", timeStr = "HH:mm:ss.SSS"
        if (dateStr.length < 10) return null
        val year = dateStr.substring(0, 4).toIntOrNull() ?: return null
        val month = dateStr.substring(5, 7).toIntOrNull() ?: return null
        val day = dateStr.substring(8, 10).toIntOrNull() ?: return null

        // timeStr could be "HH:mm:ss.SSS" or "HH:mm:ss"
        if (timeStr.length < 8) return null
        val hour = timeStr.substring(0, 2).toIntOrNull() ?: return null
        val minute = timeStr.substring(3, 5).toIntOrNull() ?: return null
        val second = timeStr.substring(6, 8).toIntOrNull() ?: return null
        val millis = if (timeStr.length > 9) {
            timeStr.substring(9).toIntOrNull() ?: 0
        } else 0

        val nanos = millis * 1_000_000
        val ldt = LocalDateTime(year, month, day, hour, minute, second, nanos)
        return ldt.toInstant(tz).toEpochMilliseconds()
    }
}
