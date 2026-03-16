package org.freewheel.core.domain

/**
 * In-memory [KeyValueStore] for testing.
 */
class FakeKeyValueStore : KeyValueStore {
    private val strings = mutableMapOf<String, String?>()
    private val stringSets = mutableMapOf<String, Set<String>>()
    private val longs = mutableMapOf<String, Long>()
    private val doubles = mutableMapOf<String, Double>()

    override fun getString(key: String, default: String?): String? =
        if (key in strings) strings[key] else default

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getStringSet(key: String): Set<String> =
        stringSets[key] ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        stringSets[key] = value.toSet()
    }

    override fun getLong(key: String, default: Long): Long =
        longs[key] ?: default

    override fun putLong(key: String, value: Long) {
        longs[key] = value
    }

    override fun getDouble(key: String, default: Double): Double =
        doubles[key] ?: default

    override fun putDouble(key: String, value: Double) {
        doubles[key] = value
    }

    override fun remove(key: String) {
        strings.remove(key)
        stringSets.remove(key)
        longs.remove(key)
        doubles.remove(key)
    }
}
