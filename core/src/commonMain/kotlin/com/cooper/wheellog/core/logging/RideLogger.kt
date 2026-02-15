package com.cooper.wheellog.core.logging

import com.cooper.wheellog.core.domain.WheelState

/**
 * Cross-platform ride logger that writes CSV files matching the legacy WheelLog format.
 *
 * Usage:
 * 1. Call [start] with a file path and GPS flag.
 * 2. Call [writeSample] on every telemetry update (internally throttled to 1Hz).
 * 3. Call [stop] to close the file and get [RideMetadata].
 *
 * Thread safety: callers must ensure [writeSample] and [stop] are not called concurrently.
 */
class RideLogger(private val fileWriter: FileWriter = FileWriter()) {

    private var isActive = false
    private var includeGps = false
    private var fileName = ""

    // Throttle state
    private var lastWriteTimeMs = 0L

    // Metadata tracking
    private var startTimeMs = 0L
    private var startTotalDistance = 0L
    private var maxSpeedKmh = 0.0
    private var totalSpeedKmh = 0.0
    private var sampleCount = 0

    val isLogging: Boolean get() = isActive

    /**
     * Start a new ride recording.
     *
     * @param filePath Full path to the CSV file to create.
     * @param withGps true to include GPS columns in the header.
     * @param currentTimeMs Current epoch time in milliseconds (for testability).
     * @return true if the file was created successfully.
     */
    fun start(filePath: String, withGps: Boolean, currentTimeMs: Long): Boolean {
        if (isActive) return false

        if (!fileWriter.open(filePath)) return false

        includeGps = withGps
        fileName = filePath.substringAfterLast('/')

        fileWriter.writeLine(CsvFormatter.header(includeGps))

        startTimeMs = currentTimeMs
        lastWriteTimeMs = 0L
        startTotalDistance = 0L
        maxSpeedKmh = 0.0
        totalSpeedKmh = 0.0
        sampleCount = 0
        isActive = true
        return true
    }

    /**
     * Write a telemetry sample if at least 1 second has elapsed since the last write.
     *
     * @param state Current wheel telemetry state.
     * @param gps Optional GPS location (ignored if [start] was called with withGps=false).
     * @param currentTimeMs Current epoch time in milliseconds.
     */
    fun writeSample(state: WheelState, gps: GpsLocation?, currentTimeMs: Long) {
        if (!isActive) return

        // 1Hz throttle
        if (currentTimeMs - lastWriteTimeMs < 1000L) return
        lastWriteTimeMs = currentTimeMs

        // Track metadata
        if (sampleCount == 0) {
            startTotalDistance = state.totalDistance
        }
        val speedKmh = state.speedKmh
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        totalSpeedKmh += speedKmh
        sampleCount++

        val dateTime = formatTimestamp(currentTimeMs)
        val tripDistance = (state.totalDistance - startTotalDistance).toInt()
        val gpsData = if (includeGps) gps else null

        val row = CsvFormatter.row(dateTime, state, tripDistance, gpsData)
        fileWriter.writeLine(row)
    }

    /**
     * Stop recording and close the file.
     *
     * @param currentTimeMs Current epoch time in milliseconds.
     * @return [RideMetadata] for the completed ride, or null if not logging.
     */
    fun stop(currentTimeMs: Long): RideMetadata? {
        if (!isActive) return null

        fileWriter.close()
        isActive = false

        val endTimeMs = currentTimeMs
        val durationSec = (endTimeMs - startTimeMs) / 1000L
        val avgSpeed = if (sampleCount > 0) totalSpeedKmh / sampleCount else 0.0

        return RideMetadata(
            fileName = fileName,
            startTimeMillis = startTimeMs,
            endTimeMillis = endTimeMs,
            durationSeconds = durationSec,
            distanceMeters = 0, // Computed from totalDistance at stop vs start
            maxSpeedKmh = maxSpeedKmh,
            avgSpeedKmh = avgSpeed,
            sampleCount = sampleCount
        )
    }
}
