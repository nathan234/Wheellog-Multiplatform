package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.ui.ControlSpec
import com.cooper.wheellog.core.ui.SettingsCommandId
import com.cooper.wheellog.core.ui.SettingsSection
import com.cooper.wheellog.core.ui.WheelSettingsConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelSettingsScreen(viewModel: WheelViewModel, onBack: () -> Unit) {
    val wheelState by viewModel.wheelState.collectAsState()
    val sections = remember(wheelState.wheelType) {
        WheelSettingsConfig.sections(wheelState.wheelType)
    }

    // Local toggle state for write-only commands
    val toggleStates = remember { mutableStateMapOf<SettingsCommandId, Boolean>() }

    // Pending confirmation dialog
    var pendingAction by remember { mutableStateOf<ControlSpec?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Wheel Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { contentPadding ->
        if (sections.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Connect to a wheel to see its settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                for (section in sections) {
                    item(key = section.title) {
                        SectionCard(
                            section = section,
                            wheelState = wheelState,
                            toggleStates = toggleStates,
                            onIntCommand = { id, value ->
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

                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // Confirmation dialog
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
}

@Composable
private fun SectionCard(
    section: SettingsSection,
    wheelState: WheelState,
    toggleStates: Map<SettingsCommandId, Boolean>,
    onIntCommand: (SettingsCommandId, Int) -> Unit,
    onBoolCommand: (SettingsCommandId, Boolean) -> Unit,
    onDangerousAction: (ControlSpec) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (section.title == "Dangerous Actions")
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (section.title == "Dangerous Actions")
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            section.controls.forEachIndexed { index, control ->
                // Check visibility gating
                if (control is ControlSpec.Slider && control.visibleWhen != null) {
                    val gateValue = toggleStates[control.visibleWhen]
                        ?: control.visibleWhen?.let { SettingsCommandId.entries.find { e -> e == it } }
                            ?.readBool(wheelState)
                        ?: false
                    if (!gateValue) return@forEachIndexed
                }

                RenderControl(
                    control = control,
                    wheelState = wheelState,
                    toggleStates = toggleStates,
                    onIntCommand = onIntCommand,
                    onBoolCommand = onBoolCommand,
                    onDangerousAction = onDangerousAction
                )

                if (index < section.controls.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun RenderControl(
    control: ControlSpec,
    wheelState: WheelState,
    toggleStates: Map<SettingsCommandId, Boolean>,
    onIntCommand: (SettingsCommandId, Int) -> Unit,
    onBoolCommand: (SettingsCommandId, Boolean) -> Unit,
    onDangerousAction: (ControlSpec) -> Unit
) {
    when (control) {
        is ControlSpec.Toggle -> ToggleControl(control, wheelState, toggleStates, onBoolCommand)
        is ControlSpec.Segmented -> SegmentedControl(control, wheelState, onIntCommand)
        is ControlSpec.Picker -> PickerControl(control, wheelState, onIntCommand)
        is ControlSpec.Slider -> SliderControl(control, onIntCommand)
        is ControlSpec.DangerousButton -> DangerousButtonControl(control, onDangerousAction)
        is ControlSpec.DangerousToggle -> DangerousToggleControl(control, toggleStates, onBoolCommand, onDangerousAction)
    }
}

@Composable
private fun ToggleControl(
    control: ControlSpec.Toggle,
    wheelState: WheelState,
    toggleStates: Map<SettingsCommandId, Boolean>,
    onBoolCommand: (SettingsCommandId, Boolean) -> Unit
) {
    val readback = control.commandId.readBool(wheelState)
    val checked = toggleStates[control.commandId] ?: readback ?: false

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(control.label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = { onBoolCommand(control.commandId, it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedControl(
    control: ControlSpec.Segmented,
    wheelState: WheelState,
    onIntCommand: (SettingsCommandId, Int) -> Unit
) {
    val readback = control.commandId.readInt(wheelState)
    var selected by remember(readback) { mutableIntStateOf(readback ?: 0) }

    Column {
        Text(control.label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            control.options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selected == index,
                    onClick = {
                        selected = index
                        onIntCommand(control.commandId, index)
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = control.options.size
                    )
                ) {
                    Text(label, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerControl(
    control: ControlSpec.Picker,
    wheelState: WheelState,
    onIntCommand: (SettingsCommandId, Int) -> Unit
) {
    val readback = control.commandId.readInt(wheelState)
    var selected by remember(readback) { mutableIntStateOf(readback?.coerceIn(0, control.options.lastIndex) ?: 0) }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = control.options.getOrElse(selected) { "${selected}" },
            onValueChange = {},
            readOnly = true,
            label = { Text(control.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            control.options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selected = index
                        expanded = false
                        onIntCommand(control.commandId, index)
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderControl(
    control: ControlSpec.Slider,
    onIntCommand: (SettingsCommandId, Int) -> Unit
) {
    var value by remember { mutableFloatStateOf(control.defaultValue.toFloat()) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(control.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${value.toInt()}${if (control.unit.isEmpty()) "" else " ${control.unit}"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onIntCommand(control.commandId, value.toInt()) },
            valueRange = control.min.toFloat()..control.max.toFloat(),
            steps = (control.max - control.min - 1).coerceAtLeast(0)
        )
    }
}

@Composable
private fun DangerousButtonControl(
    control: ControlSpec.DangerousButton,
    onDangerousAction: (ControlSpec) -> Unit
) {
    TextButton(
        onClick = { onDangerousAction(control) },
        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF44336))
    ) {
        Text(control.label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DangerousToggleControl(
    control: ControlSpec.DangerousToggle,
    toggleStates: Map<SettingsCommandId, Boolean>,
    onBoolCommand: (SettingsCommandId, Boolean) -> Unit,
    onDangerousAction: (ControlSpec) -> Unit
) {
    val checked = toggleStates[control.commandId] ?: false

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            control.label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFF44336)
        )
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                if (newValue) {
                    onDangerousAction(control)
                } else {
                    onBoolCommand(control.commandId, false)
                }
            }
        )
    }
}
