package org.freewheel.ui.theme

import androidx.compose.ui.graphics.Color
import org.freewheel.core.domain.dashboard.ColorZone

/**
 * Domain-specific colors for telemetry zones and status indicators.
 * These are intentionally NOT theme-dependent — safety/zone colors
 * must remain visually consistent regardless of Material You palette.
 */
object ZoneColors {
    val green = Color(0xFF4CAF50)
    val orange = Color(0xFFFF9800)
    val red = Color(0xFFF44336)
    val gpsCyan = Color(0xFF00BCD4)
    val warningOrange = Color(0xFFF57C00)
    val lightOnAmber = Color(0xFFFFC107)

    fun forZone(zone: ColorZone): Color = when (zone) {
        ColorZone.GREEN -> green
        ColorZone.ORANGE -> orange
        ColorZone.RED -> red
    }
}
