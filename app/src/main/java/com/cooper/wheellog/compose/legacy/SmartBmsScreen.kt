package com.cooper.wheellog.compose.legacy

import androidx.compose.runtime.*
import com.cooper.wheellog.WheelData
import com.cooper.wheellog.compose.SmartBmsContent
import com.cooper.wheellog.core.domain.BmsSnapshot
import com.cooper.wheellog.utils.SmartBms

/**
 * BMS screen reading from legacy WheelData (used in legacy MainScreen pager).
 */
@Composable
fun SmartBmsScreen() {
    val data = remember { WheelData.getInstance() }
    SmartBmsContent(
        bms1 = data.bms1.toBmsSnapshot(),
        bms2 = data.bms2.toBmsSnapshot()
    )
}

/** Convert legacy [SmartBms] to KMP [BmsSnapshot]. */
private fun SmartBms.toBmsSnapshot(): BmsSnapshot = BmsSnapshot(
    serialNumber = serialNumber,
    versionNumber = versionNumber,
    factoryCap = factoryCap,
    actualCap = actualCap,
    fullCycles = fullCycles,
    chargeCount = chargeCount,
    mfgDateStr = mfgDateStr,
    status = status,
    remCap = remCap,
    remPerc = remPerc,
    current = current,
    voltage = voltage,
    semiVoltage1 = semiVoltage1,
    semiVoltage2 = semiVoltage2,
    temp1 = temp1,
    temp2 = temp2,
    temp3 = temp3,
    temp4 = temp4,
    temp5 = temp5,
    temp6 = temp6,
    tempMos = tempMos,
    tempMosEnv = tempMosEnv,
    temp1Env = temp1Env,
    temp2Env = temp2Env,
    humidity1Env = humidity1Env,
    humidity2Env = humidity2Env,
    balanceMap = balanceMap,
    health = health,
    minCell = minCell,
    maxCell = maxCell,
    cellDiff = cellDiff,
    avgCell = avgCell,
    minCellNum = minCellNum,
    maxCellNum = maxCellNum,
    cellNum = cellNum,
    cells = cells.copyOf()
)