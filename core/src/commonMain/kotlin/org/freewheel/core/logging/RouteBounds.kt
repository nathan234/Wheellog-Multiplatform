package org.freewheel.core.logging

/**
 * Bounding box for a set of [RoutePoint]s, used to frame the camera on a map.
 */
data class RouteBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    companion object {
        /** Compute the bounding box of [points], or `null` if the list is empty. */
        fun from(points: List<RoutePoint>): RouteBounds? {
            if (points.isEmpty()) return null
            return RouteBounds(
                minLatitude = points.minOf { it.latitude },
                maxLatitude = points.maxOf { it.latitude },
                minLongitude = points.minOf { it.longitude },
                maxLongitude = points.maxOf { it.longitude }
            )
        }
    }
}
