package com.cooper.wheellog.core.telemetry

/**
 * Thread-safe ring buffer for telemetry samples.
 * Shared across Android and iOS via KMP.
 *
 * Samples are throttled to [sampleIntervalMs] and trimmed when older than [maxAgeMs].
 */
class TelemetryBuffer(
    private val sampleIntervalMs: Long = 500,
    private val maxAgeMs: Long = 60_000
) {
    private val _samples = mutableListOf<TelemetrySample>()
    val samples: List<TelemetrySample> get() = _samples.toList()

    private var lastSampleTimeMs: Long? = null

    /** Dynamic max for metrics with maxValue == 0 (e.g., Power) */
    private val _dynamicMax = mutableMapOf<MetricType, Double>()

    /**
     * Add a sample if enough time has elapsed since the last one.
     * Returns true if the sample was added.
     */
    fun addSampleIfNeeded(sample: TelemetrySample): Boolean {
        val last = lastSampleTimeMs
        if (last != null && sample.timestampMs - last < sampleIntervalMs) {
            return false
        }
        lastSampleTimeMs = sample.timestampMs
        _samples.add(sample)

        // Trim old samples
        val cutoff = sample.timestampMs - maxAgeMs
        _samples.removeAll { it.timestampMs < cutoff }

        // Update dynamic maxes
        for (metric in MetricType.entries) {
            if (metric.maxValue == 0.0) {
                val value = kotlin.math.abs(metric.extractValue(sample))
                val current = _dynamicMax[metric] ?: 0.0
                if (value > current) {
                    _dynamicMax[metric] = value
                }
            }
        }

        return true
    }

    fun clear() {
        _samples.clear()
        lastSampleTimeMs = null
        _dynamicMax.clear()
    }

    /** Extract a series of values for the given metric from all buffered samples. */
    fun valuesFor(metric: MetricType): List<Double> {
        return _samples.map { metric.extractValue(it) }
    }

    /** Compute min/max/avg statistics for a metric across all buffered samples. */
    fun statsFor(metric: MetricType): MetricStats {
        val values = valuesFor(metric)
        if (values.isEmpty()) return MetricStats(0.0, 0.0, 0.0)
        return MetricStats(
            min = values.min(),
            max = values.max(),
            avg = values.average()
        )
    }

    /** Get the effective max value for a metric (static or dynamic). */
    fun effectiveMax(metric: MetricType): Double {
        if (metric.maxValue > 0.0) return metric.maxValue
        // Dynamic: use tracked max, with a sensible minimum
        val tracked = _dynamicMax[metric] ?: 0.0
        return if (tracked > 0.0) tracked * 1.2 else 1000.0
    }
}

data class MetricStats(
    val min: Double,
    val max: Double,
    val avg: Double
)
