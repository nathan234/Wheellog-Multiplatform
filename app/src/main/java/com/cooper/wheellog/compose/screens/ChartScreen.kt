package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.core.telemetry.TelemetrySample
import com.cooper.wheellog.compose.components.ToggleChip
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

private val SPEED_COLOR = Color(0xFF2196F3)
private val CURRENT_COLOR = Color(0xFFFF9800)
private val POWER_COLOR = Color(0xFF4CAF50)
private val TEMP_COLOR = Color(0xFFF44336)
private val VOLTAGE_COLOR = Color(0xFF9C27B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    viewModel: WheelViewModel,
    onBack: () -> Unit
) {
    val samples by viewModel.telemetrySamples.collectAsState()
    val useMph = viewModel.appConfig.useMph
    val useFahrenheit = viewModel.appConfig.useFahrenheit

    var showSpeed by remember { mutableStateOf(true) }
    var showCurrent by remember { mutableStateOf(true) }
    var showPower by remember { mutableStateOf(false) }
    var showTemperature by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telemetry Chart") },
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
            // Toggle chips
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { ToggleChip("Speed", SPEED_COLOR, showSpeed, { showSpeed = !showSpeed }) }
                item { ToggleChip("Current", CURRENT_COLOR, showCurrent, { showCurrent = !showCurrent }) }
                item { ToggleChip("Power", POWER_COLOR, showPower, { showPower = !showPower }) }
                item { ToggleChip("Temp", TEMP_COLOR, showTemperature, { showTemperature = !showTemperature }) }
            }

            if (samples.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Waiting for data...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Build visible series info
                val visibleSeries = buildList {
                    if (showSpeed) add(SeriesInfo(
                        color = SPEED_COLOR,
                        values = samples.map { if (useMph) it.speedKmh * KM_TO_MILES else it.speedKmh }
                    ))
                    if (showCurrent) add(SeriesInfo(
                        color = CURRENT_COLOR,
                        values = samples.map { it.currentA }
                    ))
                    if (showPower) add(SeriesInfo(
                        color = POWER_COLOR,
                        values = samples.map { it.powerW }
                    ))
                    if (showTemperature) add(SeriesInfo(
                        color = TEMP_COLOR,
                        values = samples.map {
                            if (useFahrenheit) it.temperatureC * 9.0 / 5.0 + 32 else it.temperatureC
                        }
                    ))
                }

                if (visibleSeries.isNotEmpty()) {
                    // Main telemetry chart
                    VicoLineChart(
                        samples = samples,
                        seriesList = visibleSeries,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Voltage chart
                Text(
                    "Voltage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = VOLTAGE_COLOR,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                VicoLineChart(
                    samples = samples,
                    seriesList = listOf(
                        SeriesInfo(VOLTAGE_COLOR, samples.map { it.voltageV })
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private data class SeriesInfo(
    val color: Color,
    val values: List<Double>
)

@Composable
private fun VicoLineChart(
    samples: List<TelemetrySample>,
    seriesList: List<SeriesInfo>,
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty() || seriesList.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }

    // Update model when data or visible series change
    LaunchedEffect(samples, seriesList.map { it.values.hashCode() }) {
        modelProducer.runTransaction {
            lineSeries {
                for (info in seriesList) {
                    series(y = info.values)
                }
            }
        }
    }

    // X-axis formatter: show mm:ss timestamps
    val timeFormat = remember { SimpleDateFormat("mm:ss", Locale.US) }
    val firstTimestamp = samples.firstOrNull()?.timestampMs ?: 0L
    val bottomAxisFormatter = remember(firstTimestamp, samples.size) {
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
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
