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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.kmp.DecoderMode
import com.cooper.wheellog.compose.components.StatRow
import com.cooper.wheellog.core.domain.AppConstants
import java.util.Locale

private const val KM_TO_MILES = 0.62137119223733

@Composable
fun SettingsScreen(viewModel: WheelViewModel) {
    val appConfig = viewModel.appConfig
    val wheelState by viewModel.wheelState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val useMph = appConfig.useMph
    val useFahrenheit = appConfig.useFahrenheit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Units section
        SettingsSection(title = "Units") {
            SettingsToggle(
                label = "Use Miles per Hour",
                checked = useMph,
                onCheckedChange = { appConfig.useMph = it }
            )
            HorizontalDivider()
            SettingsToggle(
                label = "Use Fahrenheit",
                checked = useFahrenheit,
                onCheckedChange = { appConfig.useFahrenheit = it }
            )
        }

        // Alarms section
        SettingsSection(title = "Speed & Safety Alarms") {
            SettingsToggle(
                label = "Enable Alarms",
                checked = appConfig.alarmsEnabled,
                onCheckedChange = { appConfig.alarmsEnabled = it }
            )

            if (appConfig.alarmsEnabled) {
                HorizontalDivider()

                AlarmSlider(
                    label = "Alarm 1 Speed",
                    value = appConfig.alarm1Speed.toFloat(),
                    range = 0f..100f,
                    displayValue = displaySpeed(appConfig.alarm1Speed, useMph),
                    unit = if (useMph) "mph" else "km/h",
                    onValueChange = { appConfig.alarm1Speed = it.toInt() }
                )
                AlarmSlider(
                    label = "Alarm 2 Speed",
                    value = appConfig.alarm2Speed.toFloat(),
                    range = 0f..100f,
                    displayValue = displaySpeed(appConfig.alarm2Speed, useMph),
                    unit = if (useMph) "mph" else "km/h",
                    onValueChange = { appConfig.alarm2Speed = it.toInt() }
                )
                AlarmSlider(
                    label = "Alarm 3 Speed",
                    value = appConfig.alarm3Speed.toFloat(),
                    range = 0f..100f,
                    displayValue = displaySpeed(appConfig.alarm3Speed, useMph),
                    unit = if (useMph) "mph" else "km/h",
                    onValueChange = { appConfig.alarm3Speed = it.toInt() }
                )
                AlarmSlider(
                    label = "Current Alarm",
                    value = appConfig.alarmCurrent.toFloat(),
                    range = 0f..100f,
                    displayValue = appConfig.alarmCurrent,
                    unit = "A",
                    onValueChange = { appConfig.alarmCurrent = it.toInt() }
                )
                AlarmSlider(
                    label = "Temperature Alarm",
                    value = appConfig.alarmTemperature.toFloat(),
                    range = 0f..80f,
                    displayValue = displayTemperature(appConfig.alarmTemperature, useFahrenheit),
                    unit = if (useFahrenheit) "\u00B0F" else "\u00B0C",
                    onValueChange = { appConfig.alarmTemperature = it.toInt() }
                )
                AlarmSlider(
                    label = "Battery Alarm",
                    value = appConfig.alarmBattery.toFloat(),
                    range = 0f..100f,
                    displayValue = appConfig.alarmBattery,
                    unit = "%",
                    onValueChange = { appConfig.alarmBattery = it.toInt() }
                )

                Text(
                    "Set to 0 to disable individual alarms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Logging section
        SettingsSection(title = "Logging") {
            SettingsToggle(
                label = "Auto-Start Logging",
                checked = appConfig.autoLog,
                onCheckedChange = { appConfig.autoLog = it }
            )
            HorizontalDivider()
            SettingsToggle(
                label = "Include GPS",
                checked = appConfig.useGps,
                onCheckedChange = { appConfig.useGps = it }
            )
            Text(
                "GPS requires location permission. Logs are saved as CSV files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Wheel settings (when connected)
        if (connectionState.isConnected && wheelState.pedalsMode >= 0) {
            SettingsSection(title = "Wheel Settings") {
                Text(
                    "Pedals Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Hard", "Medium", "Soft").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = wheelState.pedalsMode == index,
                            onClick = { viewModel.setPedalsMode(index) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) {
                            Text(label)
                        }
                    }
                }
                Text(
                    "Pedals mode is sent to the wheel immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                val tiltBackSpeed = wheelState.tiltBackSpeed
                StatRow(
                    label = "Tilt-Back Speed",
                    value = if (tiltBackSpeed == 0) "Off"
                    else if (useMph) String.format(Locale.US, "%.0f mph", tiltBackSpeed * KM_TO_MILES)
                    else "$tiltBackSpeed km/h"
                )
                StatRow(
                    label = "Light Mode",
                    value = when (wheelState.lightMode) {
                        0 -> "Off"; 1 -> "On"; 2 -> "Strobe"; else -> "Unknown"
                    }
                )
                StatRow(
                    label = "LED Mode",
                    value = if (wheelState.ledMode < 0) "Unknown" else "${wheelState.ledMode}"
                )
            }
        }

        // Decoder mode
        SettingsSection(title = "Decoder") {
            StatRow(
                label = "Mode",
                value = "KMP"
            )
        }

        // UI section
        SettingsSection(title = "Interface") {
            SettingsToggle(
                label = "Use New Compose UI",
                checked = appConfig.useComposeUI,
                onCheckedChange = { appConfig.useComposeUI = it }
            )
            Text(
                "Restart the app to switch between legacy and Compose UI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // About section
        SettingsSection(title = "About") {
            StatRow(label = "Version", value = BuildConfig.VERSION_NAME)
            StatRow(label = "Build Date", value = BuildConfig.BUILD_DATE)
            HorizontalDivider()
            val context = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.GITHUB_REPO_URL))
                        )
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GitHub Repository")
                Text(
                    "Open",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AlarmSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: Int,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(
                "$displayValue $unit",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) - 1).toInt().coerceAtLeast(0)
        )
    }
}

private fun displaySpeed(kmh: Int, useMph: Boolean): Int =
    if (useMph) (kmh * KM_TO_MILES).toInt() else kmh

private fun displayTemperature(celsius: Int, useFahrenheit: Boolean): Int =
    if (useFahrenheit) (celsius * 9.0 / 5.0 + 32).toInt() else celsius
