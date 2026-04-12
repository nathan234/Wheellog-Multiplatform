package org.freewheel.compose.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.freewheel.core.logging.RoutePoint
import kotlin.math.cos

/**
 * Google Maps Compose view for trip detail. Shows a speed-colored polyline with
 * start/end markers and a movable selection marker linked to the chart scrubber.
 */
@Composable
fun RideMapView(
    routePoints: List<RoutePoint>,
    selectedPoint: RoutePoint?,
    onTapPoint: (RoutePoint?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState()

    val latLngList = remember(routePoints) {
        routePoints.map { LatLng(it.latitude, it.longitude) }
    }

    val colorSpans = remember(routePoints) {
        buildSpeedSpans(routePoints)
    }

    val startIcon = remember { dotIcon(0xFF4CAF50.toInt(), 20) }
    val endIcon = remember { dotIcon(0xFFF44336.toInt(), 20) }
    val selectedIcon = remember { selectedDotIcon(24) }

    // Frame the route on first load
    LaunchedEffect(latLngList) {
        if (latLngList.size >= 2) {
            val bounds = LatLngBounds.builder().apply {
                latLngList.forEach { include(it) }
            }.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 60))
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            compassEnabled = false,
            mapToolbarEnabled = false
        )
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings,
        onMapClick = { latLng ->
            val tapPoint = findNearestPoint(latLng, routePoints)
            if (tapPoint != null) {
                val zoom = cameraPositionState.position.zoom
                val threshold = tapThresholdMeters(latLng.latitude, zoom)
                val dist = haversineMeters(
                    latLng.latitude, latLng.longitude,
                    tapPoint.latitude, tapPoint.longitude
                )
                if (dist <= threshold) {
                    onTapPoint(tapPoint)
                } else {
                    onTapPoint(null)
                }
            } else {
                onTapPoint(null)
            }
        }
    ) {
        // Speed-colored polyline
        if (latLngList.size >= 2) {
            Polyline(
                points = latLngList,
                spans = colorSpans,
                width = 8f
            )
        }

        // Start marker
        latLngList.firstOrNull()?.let {
            Marker(
                state = MarkerState(position = it),
                icon = startIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                flat = true,
                zIndex = 1f
            )
        }

        // End marker
        latLngList.lastOrNull()?.let {
            Marker(
                state = MarkerState(position = it),
                icon = endIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                flat = true,
                zIndex = 1f
            )
        }

        // Selected point marker (moves with chart scrubber)
        selectedPoint?.let { pt ->
            Marker(
                state = MarkerState(position = LatLng(pt.latitude, pt.longitude)),
                icon = selectedIcon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                flat = true,
                zIndex = 2f
            )
        }
    }
}

// MARK: - Speed Color Spans

private fun buildSpeedSpans(points: List<RoutePoint>): List<StyleSpan> {
    if (points.size < 2) return emptyList()
    val speeds = points.map { it.speedKmh }
    val minSpeed = speeds.min()
    val maxSpeed = speeds.max()
    val range = maxSpeed - minSpeed

    return points.zipWithNext().map { (a, _) ->
        val fraction = if (range > 0) (a.speedKmh - minSpeed) / range else 0.0
        StyleSpan(StrokeStyle.colorBuilder(speedColor(fraction)).build())
    }
}

/** Green (0.0) -> Yellow (0.5) -> Red (1.0) */
private fun speedColor(fraction: Double): Int {
    val clamped = fraction.coerceIn(0.0, 1.0)
    val r: Float
    val g: Float
    if (clamped < 0.5) {
        val t = (clamped * 2).toFloat()
        r = t
        g = 1f
    } else {
        val t = ((clamped - 0.5) * 2).toFloat()
        r = 1f
        g = 1f - t
    }
    return android.graphics.Color.argb(255, (r * 255).toInt(), (g * 255).toInt(), 0)
}

// MARK: - Icons

private fun dotIcon(color: Int, sizePx: Int): BitmapDescriptor {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun selectedDotIcon(sizePx: Int): BitmapDescriptor {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2196F3.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val cx = sizePx / 2f
    canvas.drawCircle(cx, cx, cx - 2f, fill)
    canvas.drawCircle(cx, cx, cx - 2f, stroke)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// MARK: - Tap Handling

private fun findNearestPoint(latLng: LatLng, points: List<RoutePoint>): RoutePoint? {
    if (points.isEmpty()) return null
    var best = points[0]
    var bestDist = haversineMeters(latLng.latitude, latLng.longitude, best.latitude, best.longitude)
    for (i in 1 until points.size) {
        val p = points[i]
        val d = haversineMeters(latLng.latitude, latLng.longitude, p.latitude, p.longitude)
        if (d < bestDist) {
            bestDist = d
            best = p
        }
    }
    return best
}

/** Convert 44dp tap target to meters at the given zoom level. */
private fun tapThresholdMeters(latitude: Double, zoom: Float): Double {
    val metersPerPixel = 156543.03392 * cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom.toDouble())
    return 44 * metersPerPixel
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
