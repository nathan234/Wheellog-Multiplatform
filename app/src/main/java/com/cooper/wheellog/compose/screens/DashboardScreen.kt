package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.compose.AlarmType
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.compose.components.AlarmBanner
import com.cooper.wheellog.compose.components.ConnectionBanner
import com.cooper.wheellog.compose.components.SpeedGauge
import com.cooper.wheellog.compose.components.StatCard
import com.cooper.wheellog.compose.components.StatRow
import com.cooper.wheellog.core.domain.WheelState
import java.util.Locale

private const val KM_TO_MILES = 0.62137119223733

@Composable
fun DashboardScreen(
    viewModel: WheelViewModel,
    onNavigateToChart: () -> Unit,
    onNavigateToBms: () -> Unit = {}
) {
    val wheelState by viewModel.wheelState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val activeAlarms by viewModel.activeAlarms.collectAsState()
    val isDemo by viewModel.isDemo.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val isLightOn by viewModel.isLightOn.collectAsState()
    val useMph = viewModel.appConfig.useMph
    val useFahrenheit = viewModel.appConfig.useFahrenheit

    val displaySpeed = if (useMph) wheelState.speedKmh * KM_TO_MILES else wheelState.speedKmh
    val speedUnit = if (useMph) "mph" else "km/h"
    val maxSpeed = if (useMph) 31.0 else 50.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection banner
        ConnectionBanner(connectionState = connectionState)

        // Alarm banner
        AlarmBanner(activeAlarms = activeAlarms)

        // Speed gauge
        SpeedGauge(
            speed = displaySpeed,
            maxSpeed = maxSpeed,
            unitLabel = speedUnit,
            modifier = Modifier
                .height(250.dp)
                .padding(top = 8.dp)
        )

        // Battery and Temperature cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Battery",
                value = "${wheelState.batteryLevel}%",
                icon = batteryIcon(wheelState.batteryLevel),
                color = batteryColor(wheelState.batteryLevel),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Temperature",
                value = formatTemperature(wheelState.temperatureC, useFahrenheit),
                icon = Icons.Default.Thermostat,
                color = temperatureColor(wheelState.temperatureC),
                modifier = Modifier.weight(1f)
            )
        }

        // Power stats section
        StatsSection(modifier = Modifier.padding(horizontal = 16.dp)) {
            StatRow(label = "Voltage", value = String.format(Locale.US, "%.1f V", wheelState.voltageV))
            StatRow(label = "Current", value = String.format(Locale.US, "%.1f A", wheelState.currentA))
            StatRow(label = "Power", value = String.format(Locale.US, "%.0f W", wheelState.powerW))
            StatRow(label = "PWM", value = String.format(Locale.US, "%.1f%%", wheelState.pwmPercent))
        }

        // Distance stats
        StatsSection(modifier = Modifier.padding(horizontal = 16.dp)) {
            StatRow(label = "Trip Distance", value = formatDistance(wheelState.wheelDistanceKm, useMph))
            StatRow(label = "Total Distance", value = formatTotalDistance(wheelState.totalDistanceKm, useMph))
        }

        // Wheel settings (only when received)
        if (wheelState.pedalsMode >= 0) {
            StatsSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                StatRow(label = "Pedals Mode", value = pedalsModeText(wheelState.pedalsMode))
                StatRow(label = "Tilt-Back Speed", value = tiltBackSpeedText(wheelState.tiltBackSpeed, useMph))
                StatRow(label = "Light", value = lightModeText(wheelState.lightMode))
                StatRow(label = "LED Mode", value = "${wheelState.ledMode}")
            }
        }

        // Wheel info
        if (wheelState.name.isNotEmpty() || wheelState.model.isNotEmpty()) {
            StatsSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (wheelState.name.isNotEmpty()) {
                    StatRow(label = "Name", value = wheelState.name)
                }
                if (wheelState.model.isNotEmpty()) {
                    StatRow(label = "Model", value = wheelState.model)
                }
                StatRow(label = "Type", value = wheelState.wheelType.name)
            }
        }

        // Demo mode badge
        if (isDemo) {
            ModeBadge(
                text = "Demo Mode - Simulated Data",
                color = Color(0xFFFF9800)
            )
        }

        // Controls row: Horn, Light (hidden in demo mode)
        if (!isDemo) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.wheelBeep() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Horn")
                }
                Button(
                    onClick = { viewModel.toggleLight() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLightOn) Color(0xFFFFC107) else Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isLightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Light")
                }
            }
        }

        // Record and Chart row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (connectionState.isConnected) {
                Button(
                    onClick = { viewModel.toggleLogging() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogging) Color(0xFFF44336) else Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isLogging) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isLogging) "Stop" else "Record")
                }
            }

            Button(
                onClick = onNavigateToChart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Chart")
            }

            Button(
                onClick = onNavigateToBms,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.BatteryFull, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("BMS")
            }
        }

        // Disconnect button
        Button(
            onClick = { viewModel.disconnect() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDemo) Color(0xFFFF9800) else Color(0xFFF44336)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = if (isDemo) "Stop Demo" else "Disconnect",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatsSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ModeBadge(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = text,
                    color = color,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// --- Helpers ---

private fun batteryIcon(level: Int) = when {
    level >= 75 -> Icons.Default.BatteryFull
    level >= 50 -> Icons.Default.Battery4Bar
    level >= 25 -> Icons.Default.Battery2Bar
    else -> Icons.Default.BatteryAlert
}

private fun batteryColor(level: Int) = when {
    level >= 50 -> Color(0xFF4CAF50)
    level >= 25 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun temperatureColor(tempC: Int) = when {
    tempC <= 40 -> Color(0xFF4CAF50)
    tempC <= 55 -> Color(0xFFFF9800)
    else -> Color(0xFFF44336)
}

private fun formatTemperature(tempC: Int, useFahrenheit: Boolean): String =
    if (useFahrenheit) {
        val tempF = tempC * 9.0 / 5.0 + 32
        String.format(Locale.US, "%.0f\u00B0F", tempF)
    } else {
        "$tempC\u00B0C"
    }

private fun formatDistance(km: Double, useMph: Boolean): String =
    if (useMph) String.format(Locale.US, "%.2f mi", km * KM_TO_MILES)
    else String.format(Locale.US, "%.2f km", km)

private fun formatTotalDistance(km: Double, useMph: Boolean): String =
    if (useMph) String.format(Locale.US, "%.1f mi", km * KM_TO_MILES)
    else String.format(Locale.US, "%.1f km", km)

private fun pedalsModeText(mode: Int): String = when (mode) {
    0 -> "Hard"
    1 -> "Medium"
    2 -> "Soft"
    else -> "Unknown"
}

private fun tiltBackSpeedText(speed: Int, useMph: Boolean): String {
    if (speed == 0) return "Off"
    return if (useMph) String.format(Locale.US, "%.0f mph", speed * KM_TO_MILES)
    else "$speed km/h"
}

private fun lightModeText(mode: Int): String = when (mode) {
    0 -> "Off"
    1 -> "On"
    2 -> "Strobe"
    else -> "Unknown"
}
