package org.freewheel.core.domain

import android.content.SharedPreferences

/**
 * Android implementation of [KeyValueStore] backed by [SharedPreferences].
 */
class SharedPreferencesKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, default: String?): String? =
        prefs.getString(key, default)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long =
        prefs.getLong(key, default)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getDouble(key: String, default: Double): Double {
        return if (prefs.contains(key)) {
            java.lang.Double.longBitsToDouble(prefs.getLong(key, 0L))
        } else {
            default
        }
    }

    override fun putDouble(key: String, value: Double) {
        prefs.edit().putLong(key, java.lang.Double.doubleToRawLongBits(value)).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
