package com.cooper.wheellog.core.utils

import kotlin.math.roundToInt

/**
 * String utility functions for cross-platform use.
 */
object StringUtil {

    /**
     * Check if a string is in an array of strings.
     */
    fun inArray(value: String, array: Array<String>): Boolean {
        return value in array
    }

    /**
     * Check if a string starts with any of the given prefixes.
     */
    fun startsWithAny(value: String, prefixes: Array<String>): Boolean {
        return prefixes.any { value.startsWith(it) }
    }

    /**
     * Format duration in seconds to HH:MM:SS string.
     */
    fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "${padZero(hours)}:${padZero(minutes)}:${padZero(secs)}"
    }

    /**
     * Format distance in meters to km with specified decimal places.
     */
    fun formatDistance(meters: Long, decimals: Int = 2): String {
        val km = meters / 1000.0
        return formatDecimal(km, decimals)
    }

    /**
     * Format speed from internal units (1/100 km/h) to display string.
     */
    fun formatSpeed(speed: Int, useMph: Boolean = false): String {
        val value = speed / 100.0
        return if (useMph) {
            "${formatDecimal(value * ByteUtils.KM_TO_MILES_MULTIPLIER, 1)} mph"
        } else {
            "${formatDecimal(value, 1)} km/h"
        }
    }

    /**
     * Format temperature from internal units (1/100 °C) to display string.
     */
    fun formatTemperature(temp: Int, useFahrenheit: Boolean = false): String {
        val celsius = temp / 100
        return if (useFahrenheit) {
            "${ByteUtils.celsiusToFahrenheit(celsius.toDouble()).roundToInt()}°F"
        } else {
            "$celsius°C"
        }
    }

    /**
     * Format voltage from internal units (1/100 V) to display string.
     */
    fun formatVoltage(voltage: Int): String {
        return "${formatDecimal(voltage / 100.0, 2)}V"
    }

    /**
     * Format current from internal units (1/100 A) to display string.
     */
    fun formatCurrent(current: Int): String {
        return "${formatDecimal(current / 100.0, 2)}A"
    }

    /**
     * Format power from internal units (1/100 W) to display string.
     */
    fun formatPower(power: Int): String {
        val watts = power / 100.0
        return if (watts >= 1000) {
            "${formatDecimal(watts / 1000.0, 2)}kW"
        } else {
            "${formatDecimal(watts, 1)}W"
        }
    }

    /**
     * Format battery percentage.
     */
    fun formatBattery(percent: Int): String {
        return "$percent%"
    }

    /**
     * Pad a number with leading zero if less than 10.
     */
    private fun padZero(value: Int): String {
        return if (value < 10) "0$value" else value.toString()
    }

    /**
     * Format a double to a string with specified decimal places.
     * This is a multiplatform-compatible alternative to String.format().
     */
    fun formatDecimal(value: Double, decimals: Int): String {
        val factor = pow10(decimals)
        val rounded = (value * factor).roundToInt().toDouble() / factor
        val str = rounded.toString()

        // Ensure we have the right number of decimal places
        val dotIndex = str.indexOf('.')
        return if (dotIndex == -1) {
            if (decimals > 0) {
                "$str.${"0".repeat(decimals)}"
            } else {
                str
            }
        } else {
            val currentDecimals = str.length - dotIndex - 1
            when {
                decimals == 0 -> str.substring(0, dotIndex)  // No decimals - remove the dot
                currentDecimals < decimals -> str + "0".repeat(decimals - currentDecimals)
                currentDecimals > decimals -> str.substring(0, dotIndex + decimals + 1)
                else -> str
            }
        }
    }

    /**
     * Simple power of 10 calculation for formatting.
     */
    private fun pow10(n: Int): Int {
        var result = 1
        repeat(n) { result *= 10 }
        return result
    }
}
