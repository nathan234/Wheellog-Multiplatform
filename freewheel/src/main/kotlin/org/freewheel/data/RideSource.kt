package org.freewheel.data

/**
 * Where a trip row came from. Persisted as its [name] string in the `source`
 * column — a string (rather than an int) so sqlite dumps are readable and
 * future values can be added without version gymnastics.
 */
enum class RideSource {
    /** Captured by this device during a live ride. */
    OWN_LOG,

    /** Imported from a shared GPX (friend's ride, Strava export, etc.). */
    IMPORTED;

    companion object {
        fun fromStringOrDefault(value: String?): RideSource =
            entries.firstOrNull { it.name == value } ?: OWN_LOG
    }
}

/** Fresh UUID string for use as a trip rideId. Default value for [TripDataDbEntry.rideId]. */
fun newRideId(): String = java.util.UUID.randomUUID().toString()

