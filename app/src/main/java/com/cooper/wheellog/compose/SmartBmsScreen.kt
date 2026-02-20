package com.cooper.wheellog.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cooper.wheellog.core.domain.BmsSnapshot

/**
 * BMS screen driven by KMP WheelState via ViewModel (used in Compose Navigation).
 */
@Composable
fun SmartBmsScreen(viewModel: WheelViewModel) {
    val state by viewModel.wheelState.collectAsState()
    SmartBmsContent(bms1 = state.bms1, bms2 = state.bms2)
}

@Composable
internal fun SmartBmsContent(bms1: BmsSnapshot?, bms2: BmsSnapshot?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("BMS 1", style = MaterialTheme.typography.titleMedium)
        if (bms1 != null && bms1.voltage > 0) {
            BmsBlock(bms1)
        } else {
            Text("No BMS 1 data", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(12.dp))

        Text("BMS 2", style = MaterialTheme.typography.titleMedium)
        if (bms2 != null && bms2.voltage > 0) {
            BmsBlock(bms2)
        } else {
            Text("No BMS 2 data", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BmsBlock(bms: BmsSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (bms.serialNumber.isNotEmpty()) {
            Text("Serial: ${bms.serialNumber}")
        }
        Text("Voltage: ${String.format("%.2f V", bms.voltage)}")
        Text("Current: ${String.format("%.2f A", bms.current)}")
        if (bms.remCap > 0) {
            Text("Remaining: ${bms.remCap} / ${bms.factoryCap} mAh (${bms.remPerc}%)")
        }
        Text("Temp 1: ${String.format("%.1f\u00B0C", bms.temp1)}")
        Text("Temp 2: ${String.format("%.1f\u00B0C", bms.temp2)}")
        if (bms.health > 0) {
            Text("Health: ${bms.health}%")
        }
        Text("Max Cell: ${String.format("%.3f V [%d]", bms.maxCell, bms.maxCellNum)}")
        Text("Min Cell: ${String.format("%.3f V [%d]", bms.minCell, bms.minCellNum)}")
        Text("Cell Diff: ${String.format("%.3f V", bms.cellDiff)}")
        Text("Avg Cell: ${String.format("%.3f V", bms.avgCell)}")

        if (bms.cellNum > 0) {
            Spacer(Modifier.height(4.dp))
            Text("Cells (${bms.cellNum}):", style = MaterialTheme.typography.labelMedium)
            for (i in 0 until bms.cellNum) {
                Text("  #${i + 1}: ${String.format("%.3f V", bms.cells[i])}")
            }
        }
    }
}