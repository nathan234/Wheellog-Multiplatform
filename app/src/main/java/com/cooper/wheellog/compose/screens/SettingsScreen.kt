package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.cooper.wheellog.BuildConfig
import com.cooper.wheellog.compose.AlarmAction
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.compose.components.SectionCard
import com.cooper.wheellog.compose.components.StatRow
import com.cooper.wheellog.core.domain.AppConstants
import com.cooper.wheellog.core.domain.ControlSpec
import com.cooper.wheellog.core.domain.PreferenceKeys
import com.cooper.wheellog.core.domain.SettingsCommandId
import com.cooper.wheellog.core.domain.WheelSettingsConfig
import com.cooper.wheellog.core.utils.DisplayUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WheelViewModel) {
    val appConfig = viewModel.appConfig
    val wheelState by viewModel.wheelState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val useMph = viewModel.getGlobalBool(PreferenceKeys.USE_MPH, false)
    val useFahrenheit = viewModel.getGlobalBool(PreferenceKeys.USE_FAHRENHEIT, false)
    val context = LocalContext.current

    // Wheel settings config-driven state
    val wheelSections = remember(wheelState.wheelType) {
        WheelSettingsConfig.sections(wheelState.wheelType)
    }
    val toggleStates = remember { mutableStateMapOf<SettingsCommandId, Boolean>() }
    val sliderOverrides = remember(wheelSections) {
        val map = mutableStateMapOf<SettingsCommandId, Int>()
        for (section in wheelSections) {
            for (control in section.controls) {
                if (control is ControlSpec.Slider) {
                    val saved = viewModel.loadSliderValue(control.commandId)
                    if (saved != null) map[control.commandId] = saved
                }
            }
        }
        map
    }
    var pendingAction by remember { mutableStateOf<ControlSpec?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
                onCheckedChange = { viewModel.setGlobalBool(PreferenceKeys.USE_MPH, it) }
            )
            HorizontalDivider()
            SettingsToggle(
                label = "Use Fahrenheit",
                checked = useFahrenheit,
                onCheckedChange = { viewModel.setGlobalBool(PreferenceKeys.USE_FAHRENHEIT, it) }
            )
        }

        // Alarms section
        val alarmsEnabled = viewModel.getPerWheelBool(PreferenceKeys.ALARMS_ENABLED, false)
        val pwmBasedAlarms = viewModel.getPerWheelBool(PreferenceKeys.ALTERED_ALARMS, true)
        SettingsSection(title = "Speed & Safety Alarms") {
            SettingsToggle(
                label = "Enable Alarms",
                checked = alarmsEnabled,
                onCheckedChange = { viewModel.setPerWheelBool(PreferenceKeys.ALARMS_ENABLED, it) }
            )

            if (alarmsEnabled) {
                HorizontalDivider()

                // Alarm Action picker
                val alarmActionLabels = listOf("Phone Only", "Phone + Wheel", "All")
                var alarmActionExpanded by remember { mutableStateOf(false) }
                val selectedAction = AlarmAction.fromValue(viewModel.getGlobalInt(PreferenceKeys.ALARM_ACTION, 0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Alarm Action")
                    ExposedDropdownMenuBox(
                        expanded = alarmActionExpanded,
                        onExpandedChange = { alarmActionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = alarmActionLabels[selectedAction.value],
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = alarmActionExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = alarmActionExpanded,
                            onDismissRequest = { alarmActionExpanded = false }
                        ) {
                            alarmActionLabels.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setGlobalInt(PreferenceKeys.ALARM_ACTION, index)
                                        alarmActionExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                SettingsToggle(
                    label = "PWM-Based Alarms",
                    checked = pwmBasedAlarms,
                    onCheckedChange = { viewModel.setPerWheelBool(PreferenceKeys.ALTERED_ALARMS, it) }
                )
                Text(
                    "PWM alarms trigger based on motor load instead of speed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )

                HorizontalDivider()

                if (pwmBasedAlarms) {
                    val alarmFactor1 = viewModel.getPerWheelInt(PreferenceKeys.ALARM_FACTOR_1, 80)
                    val alarmFactor2 = viewModel.getPerWheelInt(PreferenceKeys.ALARM_FACTOR_2, 95)
                    AlarmSlider(
                        label = "Alarm Start (Factor 1)",
                        value = alarmFactor1.toFloat(),
                        range = 0f..100f,
                        displayValue = alarmFactor1,
                        unit = "%",
                        onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_FACTOR_1, it.toInt()) }
                    )
                    AlarmSlider(
                        label = "Max Intensity (Factor 2)",
                        value = alarmFactor2.toFloat(),
                        range = 0f..100f,
                        displayValue = alarmFactor2,
                        unit = "%",
                        onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_FACTOR_2, it.toInt()) }
                    )
                } else {
                    val alarm1Speed = viewModel.getPerWheelInt(PreferenceKeys.ALARM_1_SPEED, 29)
                    val alarm2Speed = viewModel.getPerWheelInt(PreferenceKeys.ALARM_2_SPEED, 0)
                    val alarm3Speed = viewModel.getPerWheelInt(PreferenceKeys.ALARM_3_SPEED, 0)
                    AlarmSlider(
                        label = "Alarm 1 Speed",
                        value = alarm1Speed.toFloat(),
                        range = 0f..100f,
                        displayValue = displaySpeed(alarm1Speed, useMph),
                        unit = if (useMph) "mph" else "km/h",
                        onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_1_SPEED, it.toInt()) }
                    )
                    AlarmSlider(
                        label = "Alarm 2 Speed",
                        value = alarm2Speed.toFloat(),
                        range = 0f..100f,
                        displayValue = displaySpeed(alarm2Speed, useMph),
                        unit = if (useMph) "mph" else "km/h",
                        onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_2_SPEED, it.toInt()) }
                    )
                    AlarmSlider(
                        label = "Alarm 3 Speed",
                        value = alarm3Speed.toFloat(),
                        range = 0f..100f,
                        displayValue = displaySpeed(alarm3Speed, useMph),
                        unit = if (useMph) "mph" else "km/h",
                        onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_3_SPEED, it.toInt()) }
                    )
                }

                val alarmCurrent = viewModel.getPerWheelInt(PreferenceKeys.ALARM_CURRENT, 0)
                val alarmTemperature = viewModel.getPerWheelInt(PreferenceKeys.ALARM_TEMPERATURE, 0)
                val alarmBattery = viewModel.getPerWheelInt(PreferenceKeys.ALARM_BATTERY, 0)
                AlarmSlider(
                    label = "Current Alarm",
                    value = alarmCurrent.toFloat(),
                    range = 0f..100f,
                    displayValue = alarmCurrent,
                    unit = "A",
                    onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_CURRENT, it.toInt()) }
                )
                AlarmSlider(
                    label = "Temperature Alarm",
                    value = alarmTemperature.toFloat(),
                    range = 0f..80f,
                    displayValue = displayTemperature(alarmTemperature, useFahrenheit),
                    unit = if (useFahrenheit) "\u00B0F" else "\u00B0C",
                    onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_TEMPERATURE, it.toInt()) }
                )
                AlarmSlider(
                    label = "Battery Alarm",
                    value = alarmBattery.toFloat(),
                    range = 0f..100f,
                    displayValue = alarmBattery,
                    unit = "%",
                    onValueChange = { viewModel.setPerWheelInt(PreferenceKeys.ALARM_BATTERY, it.toInt()) }
                )

                Text(
                    "Set to 0 to disable individual alarms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Connection section
        SettingsSection(title = "Connection") {
            SettingsToggle(
                label = "Auto Reconnect",
                checked = viewModel.getGlobalBool(PreferenceKeys.USE_RECONNECT, false),
                onCheckedChange = { viewModel.setGlobalBool(PreferenceKeys.USE_RECONNECT, it) }
            )
            Text(
                "Automatically reconnect to the last wheel on startup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Logging section
        SettingsSection(title = "Logging") {
            SettingsToggle(
                label = "Auto-Start Logging",
                checked = viewModel.getGlobalBool(PreferenceKeys.AUTO_LOG, false),
                onCheckedChange = { viewModel.setGlobalBool(PreferenceKeys.AUTO_LOG, it) }
            )
            HorizontalDivider()
            SettingsToggle(
                label = "Include GPS",
                checked = viewModel.getGlobalBool(PreferenceKeys.LOG_LOCATION_DATA, false),
                onCheckedChange = { viewModel.setGlobalBool(PreferenceKeys.LOG_LOCATION_DATA, it) }
            )
            Text(
                "GPS requires location permission. Logs are saved as CSV files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Wheel settings (config-driven, when connected)
        if (connectionState.isConnected && wheelSections.isNotEmpty()) {
            for (section in wheelSections) {
                SectionCard(
                    section = section,
                    wheelState = wheelState,
                    toggleStates = toggleStates,
                    sliderOverrides = sliderOverrides,
                    onIntCommand = { id, value ->
                        viewModel.saveSliderValue(id, value)
                        sliderOverrides[id] = value
                        viewModel.executeWheelCommand(id, intValue = value)
                    },
                    onBoolCommand = { id, value ->
                        toggleStates[id] = value
                        viewModel.executeWheelCommand(id, boolValue = value)
                    },
                    onDangerousAction = { control -> pendingAction = control }
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

        // Close app
        OutlinedButton(
            onClick = {
                viewModel.shutdownService()
                (context as? Activity)?.finishAffinity()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336))
        ) {
            Text("Close App")
        }

        Spacer(Modifier.height(16.dp))
    }

    // Confirmation dialog for dangerous wheel settings actions
    pendingAction?.let { action ->
        when (action) {
            is ControlSpec.DangerousButton -> {
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text(action.confirmTitle) },
                    text = { Text(action.confirmMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.executeWheelCommand(action.commandId)
                                pendingAction = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingAction = null }) { Text("Cancel") }
                    }
                )
            }
            is ControlSpec.DangerousToggle -> {
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text(action.confirmTitle) },
                    text = { Text(action.confirmMessage) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                toggleStates[action.commandId] = true
                                viewModel.executeWheelCommand(action.commandId, boolValue = true)
                                pendingAction = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
                        ) { Text("Confirm") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingAction = null }) { Text("Cancel") }
                    }
                )
            }
            else -> { pendingAction = null }
        }
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
    DisplayUtils.convertSpeed(kmh.toDouble(), useMph).toInt()

private fun displayTemperature(celsius: Int, useFahrenheit: Boolean): Int =
    DisplayUtils.convertTemp(celsius.toDouble(), useFahrenheit).toInt()
