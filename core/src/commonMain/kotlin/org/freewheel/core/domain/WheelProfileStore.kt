package org.freewheel.core.domain

/**
 * Manages saved wheel profiles ("My Wheels" garage) using a [KeyValueStore].
 *
 * Storage layout:
 * - `saved_wheel_addresses` : Set<String> of addresses
 * - `{address}_profile_name`    : display name
 * - `{address}_wheel_type_name` : WheelType enum name
 * - `{address}_last_connected`  : epoch millis
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
                lastConnectedMs = store.getLong(address + PreferenceKeys.SUFFIX_LAST_CONNECTED, 0L)
            )
        }.sortedByDescending { it.lastConnectedMs }
    }

    fun getDisplayName(address: String): String? {
        val name = store.getString(address + PreferenceKeys.SUFFIX_PROFILE_NAME, null)
        return if (name.isNullOrEmpty()) null else name
    }

    fun saveProfile(profile: WheelProfile) {
        val addresses = getSavedAddresses().toMutableSet()
        addresses.add(profile.address)
        store.putStringSet(PreferenceKeys.SAVED_WHEEL_ADDRESSES, addresses)
        store.putString(profile.address + PreferenceKeys.SUFFIX_PROFILE_NAME, profile.displayName)
        store.putString(profile.address + PreferenceKeys.SUFFIX_WHEEL_TYPE, profile.wheelTypeName)
        store.putLong(profile.address + PreferenceKeys.SUFFIX_LAST_CONNECTED, profile.lastConnectedMs)
    }

    fun deleteProfile(address: String) {
        val addresses = getSavedAddresses().toMutableSet()
        addresses.remove(address)
        store.putStringSet(PreferenceKeys.SAVED_WHEEL_ADDRESSES, addresses)
        store.remove(address + PreferenceKeys.SUFFIX_WHEEL_TYPE)
        store.remove(address + PreferenceKeys.SUFFIX_LAST_CONNECTED)
        // Keep profile_name — it's shared with per-wheel settings
    }
}
