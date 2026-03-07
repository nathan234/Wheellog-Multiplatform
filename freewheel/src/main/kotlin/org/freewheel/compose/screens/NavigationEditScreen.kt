package org.freewheel.compose.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.freewheel.compose.WheelViewModel
import org.freewheel.compose.navigation.tabIcon
import org.freewheel.core.domain.dashboard.NavigationConfig
import org.freewheel.core.domain.dashboard.NavigationTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationEditScreen(
    viewModel: WheelViewModel,
    onBack: () -> Unit,
    onNavigateToEditCustomTab: (String) -> Unit = {}
) {
    val currentConfig by viewModel.navigationConfig.collectAsStateWithLifecycle()
    val activeTabs = remember(currentConfig) { mutableStateListOf(*currentConfig.tabs.toTypedArray()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val config = NavigationConfig(tabs = activeTabs.toList())
    val isConfigValid = config.isValid()
    val warnings = config.warnings()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Customize Navigation") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancel") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isConfigValid) {
                                viewModel.saveNavigationConfig(config)
                            }
                            onBack()
                        },
                        enabled = isConfigValid
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Choose which screens appear as tabs. Devices is always included.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Built-in tab list
            Text(
                "Built-in Tabs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    NavigationTab.builtIn.forEachIndexed { index, tab ->
                        val isActive = activeTabs.any { it.id == tab.id }
                        val activeIndex = activeTabs.indexOfFirst { it.id == tab.id }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    tabIcon(tab),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    tab.label,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }

                            if (isActive && activeIndex >= 0) {
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (activeIndex > 0) {
                                                val item = activeTabs.removeAt(activeIndex)
                                                activeTabs.add(activeIndex - 1, item)
                                            }
                                        },
                                        enabled = activeIndex > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, "Move up")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (activeIndex < activeTabs.size - 1) {
                                                val item = activeTabs.removeAt(activeIndex)
                                                activeTabs.add(activeIndex + 1, item)
                                            }
                                        },
                                        enabled = activeIndex < activeTabs.size - 1
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, "Move down")
                                    }
                                }
                            }

                            Switch(
                                checked = isActive,
                                onCheckedChange = { checked ->
                                    if (tab.isRequired) return@Switch
                                    if (checked) {
                                        if (activeTabs.size < 5) activeTabs.add(tab)
                                    } else {
                                        if (activeTabs.size > 2) activeTabs.removeAll { it.id == tab.id }
                                    }
                                },
                                enabled = !tab.isRequired
                            )
                        }

                        if (index < NavigationTab.builtIn.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Custom tabs section
            Text(
                "Custom Tabs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            val customTabs = activeTabs.filterIsInstance<NavigationTab.Custom>()
            if (customTabs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        customTabs.forEachIndexed { index, tab ->
                            val activeIndex = activeTabs.indexOfFirst { it.id == tab.id }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        tabIcon(tab),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        tab.label,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }

                                if (activeIndex >= 0) {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                if (activeIndex > 0) {
                                                    val item = activeTabs.removeAt(activeIndex)
                                                    activeTabs.add(activeIndex - 1, item)
                                                }
                                            },
                                            enabled = activeIndex > 0
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, "Move up")
                                        }
                                        IconButton(
                                            onClick = {
                                                if (activeIndex < activeTabs.size - 1) {
                                                    val item = activeTabs.removeAt(activeIndex)
                                                    activeTabs.add(activeIndex + 1, item)
                                                }
                                            },
                                            enabled = activeIndex < activeTabs.size - 1
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, "Move down")
                                        }
                                    }
                                }

                                // Edit layout button
                                IconButton(onClick = {
                                    // Save first so layout is persisted
                                    if (isConfigValid) {
                                        viewModel.saveNavigationConfig(NavigationConfig(tabs = activeTabs.toList()))
                                    }
                                    onNavigateToEditCustomTab(tab.id)
                                }) {
                                    Icon(Icons.Default.Edit, "Edit layout")
                                }

                                // Delete button
                                IconButton(onClick = {
                                    activeTabs.removeAll { it.id == tab.id }
                                    viewModel.deleteCustomTabLayout(tab.id)
                                }) {
                                    Icon(Icons.Default.Close, "Remove")
                                }
                            }

                            if (index < customTabs.size - 1) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Create custom tab button
            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                enabled = activeTabs.size < 5
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Create Custom Tab", modifier = Modifier.padding(start = 4.dp))
            }

            if (activeTabs.size >= 5) {
                Text(
                    "Maximum 5 tabs. Remove a tab to add another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Warnings
            if (warnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        warnings.forEach { warning ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Live preview
            Text(
                "Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            NavigationBar {
                activeTabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = tab.id == NavigationTab.Devices.id,
                        onClick = {}
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Create custom tab dialog
    if (showCreateDialog) {
        CreateCustomTabDialog(
            existingIds = activeTabs.map { it.id }.toSet(),
            onConfirm = { tab ->
                if (activeTabs.size < 5) {
                    activeTabs.add(tab)
                }
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateCustomTabDialog(
    existingIds: Set<String>,
    onConfirm: (NavigationTab.Custom) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(NavigationTab.customIcons.first()) }

    val trimmedName = name.trim()
    val generatedId = trimmedName.lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
        .take(30)
    val isValid = trimmedName.isNotBlank() && generatedId.isNotEmpty() && generatedId !in existingIds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Tab") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Tab Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Icon",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (iconName in NavigationTab.customIcons) {
                        val isSelected = iconName == selectedIcon
                        val tempTab = NavigationTab.Custom(id = "", label = "", iconName = iconName)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ).border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    else Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                    )
                                )
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                tabIcon(tempTab),
                                contentDescription = iconName,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (generatedId in existingIds && trimmedName.isNotBlank()) {
                    Text(
                        "A tab with this name already exists",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        NavigationTab.Custom(
                            id = generatedId,
                            label = trimmedName,
                            iconName = selectedIcon
                        )
                    )
                },
                enabled = isValid
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
