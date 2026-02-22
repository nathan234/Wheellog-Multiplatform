package com.cooper.wheellog.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cooper.wheellog.core.telemetry.MetricType
import com.cooper.wheellog.core.telemetry.TelemetrySample
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SPEED_COLOR = Color(0xFF2196F3)
val GPS_SPEED_COLOR = Color(0xFF00BCD4)
val CURRENT_COLOR = Color(0xFFFF9800)
val POWER_COLOR = Color(0xFF4CAF50)
val TEMP_COLOR = Color(0xFFF44336)
val PWM_COLOR = Color(0xFFE91E63)
val VOLTAGE_COLOR = Color(0xFF9C27B0)

data class SeriesInfo(
    val color: Color,
    val values: List<Double>
)

fun metricColor(metric: MetricType): Color = when (metric) {
    MetricType.SPEED -> SPEED_COLOR
    MetricType.BATTERY -> POWER_COLOR
    MetricType.POWER -> CURRENT_COLOR
    MetricType.PWM -> VOLTAGE_COLOR
    MetricType.TEMPERATURE -> TEMP_COLOR
    MetricType.GPS_SPEED -> GPS_SPEED_COLOR
}

@Composable
fun VicoLineChart(
    samples: List<TelemetrySample>,
    seriesList: List<SeriesInfo>,
    modifier: Modifier = Modifier,
    timeFormatPattern: String = "HH:mm",
    marker: CartesianMarker? = null,
) {
    if (samples.isEmpty() || seriesList.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(samples, seriesList.map { it.values.hashCode() }) {
        modelProducer.runTransaction {
            lineSeries {
                for (info in seriesList) {
                    series(y = info.values)
                }
            }
        }
    }

    val timeFormat = remember(timeFormatPattern) { SimpleDateFormat(timeFormatPattern, Locale.US) }
    val firstTimestamp = samples.firstOrNull()?.timestampMs ?: 0L
    val bottomAxisFormatter = remember(firstTimestamp, samples.size, timeFormatPattern) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt().coerceIn(0, samples.lastIndex)
            timeFormat.format(Date(samples[index].timestampMs))
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    seriesList.map { info ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(info.color)),
                            areaFill = null,
                        )
                    }
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
            marker = marker,
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
