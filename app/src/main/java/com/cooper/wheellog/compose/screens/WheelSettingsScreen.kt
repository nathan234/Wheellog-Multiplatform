package com.cooper.wheellog.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cooper.wheellog.compose.WheelViewModel
import com.cooper.wheellog.compose.components.SectionCard
import com.cooper.wheellog.core.domain.ControlSpec
import com.cooper.wheellog.core.domain.SettingsCommandId
import com.cooper.wheellog.core.domain.WheelSettingsConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelSettingsScreen(viewModel: WheelViewModel, onBack: () -> Unit) {
    val wheelState by viewModel.wheelState.collectAsState()
    val sections = remember(wheelState.wheelType) {
        WheelSettingsConfig.sections(wheelState.wheelType)
    }

    // Local toggle state for write-only commands
    val toggleStates = remember { mutableStateMapOf<SettingsCommandId, Boolean>() }

    // Persisted slider values for write-only commands (e.g. beeper volume)
    val sliderOverrides = remember(sections) {
        val map = mutableStateMapOf<SettingsCommandId, Int>()
        for (section in sections) {
            for (control in section.controls) {
                if (control is ControlSpec.Slider) {
                    val saved = viewModel.loadSliderValue(control.commandId)
                    if (saved != null) map[control.commandId] = saved
                }
            }
        }
        map
    }

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
                },
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
