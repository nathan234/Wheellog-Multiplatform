package com.cooper.wheellog.compose.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.core.telemetry.ColorZone
import com.cooper.wheellog.core.telemetry.MetricType
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SpeedGauge(
    speed: Double,
    maxSpeed: Double,
    unitLabel: String = "km/h",
    modifier: Modifier = Modifier
) {
    val progress = (speed / maxSpeed).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speed_progress"
    )

    val speedColor = when (MetricType.SPEED.colorZone(progress.toDouble())) {
        ColorZone.GREEN -> Color(0xFF4CAF50)
        ColorZone.ORANGE -> Color(0xFFFF9800)
        ColorZone.RED -> Color(0xFFF44336)
    }

    val textMeasurer = rememberTextMeasurer()
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = min(size.width, size.height)
            val lineWidth = canvasSize * 0.08f
            val radius = (canvasSize - lineWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Arc spans from 144° to -144° (288° total), starting at bottom-left
            val startAngle = 144f  // degrees from top (12 o'clock), going clockwise
            val sweepAngle = 252f  // total arc sweep (360 - 108 gap at bottom)

            // Background arc
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = lineWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Progress arc
            drawArc(
                color = speedColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animatedProgress,
                useCenter = false,
                style = Stroke(width = lineWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            // Tick labels
            val tickInterval = 10
            val labelRadius = radius - lineWidth - 10f
            for (tickSpeed in 0..maxSpeed.toInt() step tickInterval) {
                val tickProgress = tickSpeed.toFloat() / maxSpeed.toFloat()
                val angleDeg = startAngle + sweepAngle * tickProgress
                val angleRad = angleDeg * PI.toFloat() / 180f

                val x = center.x + labelRadius * cos(angleRad)
                val y = center.y + labelRadius * sin(angleRad)

                val labelText = "$tickSpeed"
                val textLayout = textMeasurer.measure(
                    labelText,
                    style = TextStyle(fontSize = 10.sp, color = secondaryColor)
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        x - textLayout.size.width / 2f,
                        y - textLayout.size.height / 2f
                    )
                )
            }
        }

        // Center content
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.1f", speed),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = speedColor
                )
            )
            Text(
                text = unitLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
