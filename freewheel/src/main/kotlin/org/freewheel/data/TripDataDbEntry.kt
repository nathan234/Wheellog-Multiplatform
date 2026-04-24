package org.freewheel.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_database",
    indices = [
        Index(value = ["fileName"], unique = true),
        Index(value = ["rideId"], unique = true),
    ],
)
data class TripDataDbEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fileName: String,
    var start: Int = 0,
    /**
     * Duration in minutes
     */
    var duration: Int = 0,
    var maxSpeed: Float = 0f,
    var avgSpeed: Float = 0f,
    var maxPwm: Float = 0f,
    var maxCurrent: Float = 0f,
    var maxPower: Float = 0f,
    var distance: Int = 0,
    var consumptionTotal: Float = 0f,
    var consumptionByKm: Float = 0f,
    /**
     * Stable ride identity that survives sharing. UUID string, lowercase.
     * Primary dedup key when importing a shared ride. Defaults to a fresh
     * UUID so existing construction sites keep working — Room passes the
     * stored value through the constructor on read, so this default only
     * runs when a caller omits the argument.
     */
    var rideId: String = newRideId(),
    /**
     * [RideSource] name (OWN_LOG / IMPORTED). Stored as String so DB dumps
     * are readable and future sources can be added without migration churn.
     */
    var source: String = RideSource.OWN_LOG.name,
)