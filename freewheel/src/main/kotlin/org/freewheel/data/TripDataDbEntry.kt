package org.freewheel.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_database",
    indices = [Index(
        value = ["fileName"],
        unique = true
    )]
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
    var consumptionByKm: Float = 0f
)