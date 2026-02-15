package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.core.telemetry.MetricType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KM_TO_MILES = 0.62137119223733

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailScreen(
    viewModel: WheelViewModel,
    metricId: String,
    onBack: () -> Unit
) {
    val samples by viewModel.telemetrySamples.collectAsState()
    val useMph = viewModel.appConfig.useMph
    val useFahrenheit = viewModel.appConfig.useFahrenheit

    val metric = remember(metricId) {
        MetricType.entries.firstOrNull { it.name.lowercase() == metricId.lowercase() }
            ?: MetricType.SPEED
    }

    // Determine display unit and conversion
    val displayUnit = when (metric) {
        MetricType.SPEED, MetricType.GPS_SPEED ->
            if (useMph) "mph" else "km/h"
        MetricType.TEMPERATURE ->
            if (useFahrenheit) "\u00B0F" else "\u00B0C"
        else -> metric.unit
    }

    val values = samples.map { sample ->
        val raw = metric.extractValue(sample)
        when (metric) {
            MetricType.SPEED, MetricType.GPS_SPEED ->
                if (useMph) raw * KM_TO_MILES else raw
            MetricType.TEMPERATURE ->
                if (useFahrenheit) raw * 9.0 / 5.0 + 32 else raw
            else -> raw
        }
    }

    val currentValue = values.lastOrNull() ?: 0.0
    val stats = viewModel.telemetryBuffer.statsFor(metric)

    // Apply unit conversion to stats
    fun convertStat(v: Double): Double = when (metric) {
        MetricType.SPEED, MetricType.GPS_SPEED ->
            if (useMph) v * KM_TO_MILES else v
        MetricType.TEMPERATURE ->
            if (useFahrenheit) v * 9.0 / 5.0 + 32 else v
        else -> v
    }

    val chartColor = metricColor(metric)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(metric.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Current value
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatMetricValue(currentValue, metric),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = chartColor
                )
                Text(
                    text = displayUnit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Chart
            if (values.isNotEmpty()) {
                val modelProducer = remember { CartesianChartModelProducer() }

                LaunchedEffect(values) {
                    modelProducer.runTransaction {
                        lineSeries { series(y = values) }
                    }
                }

                val timeFormat = remember { SimpleDateFormat("mm:ss", Locale.US) }
                val bottomAxisFormatter = remember(samples.size) {
                    CartesianValueFormatter { _, value, _ ->
                        val index = value.toInt().coerceIn(0, samples.lastIndex)
                        timeFormat.format(Date(samples[index].timestampMs))
                    }
                }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(chartColor)),
                                    areaFill = null,
                                )
                            )
                        ),
                        startAxis = VerticalAxis.rememberStart(),
                        bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Stats
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Min", formatMetricValue(convertStat(stats.min), metric), displayUnit)
                    StatItem("Avg", formatMetricValue(convertStat(stats.avg), metric), displayUnit)
                    StatItem("Max", formatMetricValue(convertStat(stats.max), metric), displayUnit)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMetricValue(value: Double, metric: MetricType): String = when (metric) {
    MetricType.BATTERY -> String.format(Locale.US, "%.0f", value)
    MetricType.POWER -> String.format(Locale.US, "%.0f", value)
    MetricType.PWM -> String.format(Locale.US, "%.1f", value)
    MetricType.TEMPERATURE -> String.format(Locale.US, "%.0f", value)
    else -> String.format(Locale.US, "%.1f", value)
}

internal fun metricColor(metric: MetricType): Color = when (metric) {
    MetricType.SPEED -> Color(0xFF2196F3)
    MetricType.BATTERY -> Color(0xFF4CAF50)
    MetricType.POWER -> Color(0xFFFF9800)
    MetricType.PWM -> Color(0xFF9C27B0)
    MetricType.TEMPERATURE -> Color(0xFFF44336)
    MetricType.GPS_SPEED -> Color(0xFF00BCD4)
}
