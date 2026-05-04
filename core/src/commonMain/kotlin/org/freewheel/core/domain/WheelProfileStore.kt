package org.freewheel.core.domain

/**
 * Manages saved wheel profiles ("My Wheels" garage) using a [KeyValueStore].
 *
 * Storage layout:
 * - `saved_wheel_addresses` : Set<String> of addresses
 * - `{address}_profile_name`             : display name
 * - `{address}_wheel_type_name`          : WheelType enum name
 * - `{address}_last_connected`           : epoch millis
 * - `{address}_top_speed_override_kmh`   : Double, sentinel 0.0 = unset
 */
class WheelProfileStore(private val store: KeyValueStore) {

    fun getSavedAddresses(): Set<String> {
        return store.getStringSet(PreferenceKeys.SAVED_WHEEL_ADDRESSES)
    }

    fun getSavedProfiles(): List<WheelProfile> {
        return getSavedAddresses().map { address ->
            WheelProfile(
                address = address,
                displayName = store.getString(address + PreferenceKeys.SUFFIX_PROFILE_NAME, "") ?: "",
                wheelTypeName = store.getString(address + PreferenceKeys.SUFFIX_WHEEL_TYPE, "") ?: "",
                lastConnectedMs = store.getLong(address + PreferenceKeys.SUFFIX_LAST_CONNECTED, 0L),
                topSpeedOverrideKmh = readTopSpeedOverride(address),
            )
        }.sortedByDescending { it.lastConnectedMs }
    }

    fun getDisplayName(address: String): String? {
        val name = store.getString(address + PreferenceKeys.SUFFIX_PROFILE_NAME, null)
        return if (name.isNullOrEmpty()) null else name
    }

    fun getTopSpeedOverrideKmh(address: String): Double? = readTopSpeedOverride(address)

    fun setTopSpeedOverrideKmh(address: String, kmh: Double?) {
        val key = address + PreferenceKeys.SUFFIX_TOP_SPEED_OVERRIDE_KMH
        if (kmh == null || kmh <= 0.0) {
            store.remove(key)
        } else {
            store.putDouble(key, kmh)
        }
    }

    fun saveProfile(profile: WheelProfile) {
        val addresses = getSavedAddresses().toMutableSet()
        addresses.add(profile.address)
        store.putStringSet(PreferenceKeys.SAVED_WHEEL_ADDRESSES, addresses)
        store.putString(profile.address + PreferenceKeys.SUFFIX_PROFILE_NAME, profile.displayName)
        store.putString(profile.address + PreferenceKeys.SUFFIX_WHEEL_TYPE, profile.wheelTypeName)
        store.putLong(profile.address + PreferenceKeys.SUFFIX_LAST_CONNECTED, profile.lastConnectedMs)
        setTopSpeedOverrideKmh(profile.address, profile.topSpeedOverrideKmh)
    }

    fun deleteProfile(address: String) {
        val addresses = getSavedAddresses().toMutableSet()
        addresses.remove(address)
        store.putStringSet(PreferenceKeys.SAVED_WHEEL_ADDRESSES, addresses)
        store.remove(address + PreferenceKeys.SUFFIX_WHEEL_TYPE)
        store.remove(address + PreferenceKeys.SUFFIX_LAST_CONNECTED)
        store.remove(address + PreferenceKeys.SUFFIX_TOP_SPEED_OVERRIDE_KMH)
        // Keep profile_name — it's shared with per-wheel settings
    }

    /** Reads the override, treating the 0.0 sentinel (= unset) as null. */
    private fun readTopSpeedOverride(address: String): Double? {
        val raw = store.getDouble(address + PreferenceKeys.SUFFIX_TOP_SPEED_OVERRIDE_KMH, 0.0)
        return raw.takeIf { it > 0.0 }
    }
}
