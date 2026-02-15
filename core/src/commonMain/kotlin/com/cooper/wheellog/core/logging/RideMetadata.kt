package com.cooper.wheellog.core.logging

/**
 * Metadata about a completed ride recording.
 */
data class RideMetadata(
    val fileName: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val distanceMeters: Long,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val sampleCount: Int
)
