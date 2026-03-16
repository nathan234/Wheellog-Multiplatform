package org.freewheel.core.domain

/**
 * Platform-agnostic key-value store interface.
 *
 * Android: backed by SharedPreferences.
 * iOS: backed by NSUserDefaults.
 */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getDouble(key: String, default: Double): Double
    fun putDouble(key: String, value: Double)
    fun remove(key: String)
}
