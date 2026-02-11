package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.SmartBms
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.util.ByteUtils
import com.cooper.wheellog.core.utils.Lock
import com.cooper.wheellog.core.utils.withLock
import kotlin.math.roundToInt

/**
 * KingSong protocol decoder.
 *
 * KingSong uses fixed 20-byte frames with header AA 55.
 * Frame type is indicated by byte 16.
 *
 * Supports:
 * - KS-14, KS-16, KS-18 series
 * - KS-S18, KS-S16, KS-S19, KS-S20, KS-S22
 * - KS-F18P, KS-F22P
 *
 * Thread-safe: All mutable state is protected by a lock.
 */
class KingsongDecoder : WheelDecoder {

    override val wheelType: WheelType = WheelType.KINGSONG

    // Lock to protect mutable state from concurrent access
    private val stateLock = Lock()

    // Mutable state (protected by stateLock)
    private var ksAlarm1Speed = 0
    private var ksAlarm2Speed = 0
    private var ksAlarm3Speed = 0
    private var wheelMaxSpeed = 0
    private var m18Lkm = true
    private var mMode = 0
    private var mSpeedLimit = 0.0
    private var model = ""
    private var name = ""
    private var serialNumber = ""
    private var version = ""
    private var bms1 = SmartBms()
    private var bms2 = SmartBms()

    override fun decode(data: ByteArray, currentState: WheelState, config: DecoderConfig): DecodedData? {
        if (data.size < 20) return null

        // Check header (AA 55)
        val a1 = data[0].toInt() and 0xFF
        val a2 = data[1].toInt() and 0xFF
        if (a1 != 0xAA || a2 != 0x55) return null

        val frameType = data[16].toInt() and 0xFF
        val commands = mutableListOf<WheelCommand>()

        return stateLock.withLock {
            val newState = when (frameType) {
                0xA9 -> processLiveData(data, currentState, config)
                0xB9 -> processDistanceTimeData(data, currentState)
                0xBB -> processNameTypeData(data, currentState)
                0xB3 -> processSerialNumber(data, currentState, commands)
                0xF5 -> processCpuLoadPwm(data, currentState)
                0xF6 -> processSpeedLimit(data, currentState)
                0xA4, 0xB5 -> processMaxSpeedAlerts(data, currentState, commands)
                0xF1, 0xF2 -> processBmsData(data, currentState, frameType)
                0xE1, 0xE2 -> processBmsSerial(data, frameType)
                0xE5, 0xE6 -> processBmsFirmware(data, frameType)
                else -> null
            }

            if (newState != null) {
                DecodedData(
                    newState = newState.copy(bms1 = bms1, bms2 = bms2),
                    commands = commands,
                    hasNewData = frameType == 0xA9 || frameType == 0xA4 || frameType == 0xB5
                )
            } else null
        }
    }

    /**
     * Frame 0xA9: Live telemetry data
     */
    private fun processLiveData(
        data: ByteArray,
        currentState: WheelState,
        config: DecoderConfig
    ): WheelState {
        val voltage = ByteUtils.getInt2R(data, 2)
        val speed = ByteUtils.getInt2R(data, 4)
        var totalDistance = ByteUtils.getInt4R(data, 6).toLong()

        // KS-18L distance scaling
        if (model == "KS-18L" && !m18Lkm) {
            totalDistance = (totalDistance * KS18L_SCALER).roundToInt().toLong()
        }

        val current = (data[10].toInt() and 0xFF) + (data[11].toInt() shl 8)
        val temperature = ByteUtils.getInt2R(data, 12)

        // Mode info
        if ((data[15].toInt() and 0xFF) == 224) {
            mMode = data[14].toInt()
        }

        // Calculate battery percentage
        val battery = calculateBatteryPercent(voltage, config.useCustomPercents)

        // Calculate power
        val power = ((current / 100.0) * voltage).roundToInt()

        return currentState.copy(
            speed = speed,
            voltage = voltage,
            current = current,
            power = power,
            temperature = temperature,
            totalDistance = totalDistance,
            batteryLevel = battery,
            modeStr = mMode.toString(),
            model = model,
            name = name,
            serialNumber = serialNumber,
            version = version,
            wheelType = WheelType.KINGSONG
        )
    }

    /**
     * Frame 0xB9: Distance/Time/Fan data
     */
    private fun processDistanceTimeData(data: ByteArray, currentState: WheelState): WheelState {
        val distance = ByteUtils.getInt4R(data, 2).toLong()
        val topSpeed = ByteUtils.getInt2R(data, 8)
        val fanStatus = data[12].toInt()
        val chargingStatus = data[13].toInt()
        val temperature2 = ByteUtils.getInt2R(data, 14)

        return currentState.copy(
            wheelDistance = distance,
            fanStatus = fanStatus,
            chargingStatus = chargingStatus,
            temperature2 = temperature2
        )
    }

    /**
     * Frame 0xBB: Name and Type data
     */
    private fun processNameTypeData(data: ByteArray, currentState: WheelState): WheelState {
        var end = 0
        var i = 0
        while (i < 14 && data[i + 2].toInt() != 0) {
            end++
            i++
        }

        name = data.decodeToString(2, 2 + end).trim()

        // Extract model from name (e.g., "KS-16X-1234" -> "KS-16X")
        val parts = name.split("-")
        model = if (parts.size > 1) {
            parts.dropLast(1).joinToString("-")
        } else {
            name
        }

        // Extract version from last part
        if (parts.size > 1) {
            try {
                val verNum = parts.last().toInt()
                val major = verNum / 100
                val minor = verNum % 100
                version = "$major.${minor.toString().padStart(2, '0')}"
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        return currentState.copy(
            name = name,
            model = model,
            version = version
        )
    }

    /**
     * Frame 0xB3: Serial Number
     */
    private fun processSerialNumber(
        data: ByteArray,
        currentState: WheelState,
        commands: MutableList<WheelCommand>
    ): WheelState {
        val snData = ByteArray(18)
        data.copyInto(snData, 0, 2, 16)
        data.copyInto(snData, 14, 17, 20)
        snData[17] = 0

        serialNumber = snData.decodeToString().trim { it <= ' ' || it == '\u0000' }

        // Request alarm and speed settings after getting serial
        commands.add(WheelCommand.SendBytes(createRequest(0x98)))

        return currentState.copy(serialNumber = serialNumber)
    }

    /**
     * Frame 0xF5: CPU load and PWM
     */
    private fun processCpuLoadPwm(data: ByteArray, currentState: WheelState): WheelState {
        val cpuLoad = data[14].toInt()
        val output = (data[15].toInt() and 0xFF) * 100
        val calculatedPwm = output / 10000.0

        return currentState.copy(
            cpuLoad = cpuLoad,
            output = output,
            calculatedPwm = calculatedPwm
        )
    }

    /**
     * Frame 0xF6: Speed limit
     */
    private fun processSpeedLimit(data: ByteArray, currentState: WheelState): WheelState {
        mSpeedLimit = ByteUtils.getInt2R(data, 2) / 100.0
        return currentState.copy(speedLimit = mSpeedLimit)
    }

    /**
     * Frame 0xA4/0xB5: Max speed and alerts
     */
    private fun processMaxSpeedAlerts(
        data: ByteArray,
        currentState: WheelState,
        commands: MutableList<WheelCommand>
    ): WheelState {
        wheelMaxSpeed = data[10].toInt() and 0xFF
        ksAlarm3Speed = data[8].toInt() and 0xFF
        ksAlarm2Speed = data[6].toInt() and 0xFF
        ksAlarm1Speed = data[4].toInt() and 0xFF

        // Respond to 0xA4 with 0x98
        if ((data[16].toInt() and 0xFF) == 0xA4) {
            val response = data.copyOf()
            response[16] = 0x98.toByte()
            commands.add(WheelCommand.SendBytes(response))
        }

        return currentState
    }

    /**
     * Frame 0xF1/0xF2: BMS data
     */
    private fun processBmsData(data: ByteArray, currentState: WheelState, frameType: Int): WheelState {
        val bmsNum = (frameType and 0xFF) - 0xF0
        val bms = if (bmsNum == 1) bms1 else bms2
        val pNum = data[17].toInt() and 0xFF

        when (pNum) {
            0x00 -> {
                bms.voltage = ByteUtils.getInt2R(data, 2) / 100.0
                bms.current = ByteUtils.getInt2R(data, 4) / 100.0
                bms.remCap = ByteUtils.getInt2R(data, 6) * 10
                bms.factoryCap = ByteUtils.getInt2R(data, 8) * 10
                bms.fullCycles = ByteUtils.getInt2R(data, 10)
                bms.remPerc = if (bms.factoryCap > 0) {
                    (bms.remCap / (bms.factoryCap / 100.0)).roundToInt()
                } else 0
            }
            0x01 -> {
                bms.temp1 = (ByteUtils.getInt2R(data, 2) - 2730) / 10.0
                bms.temp2 = (ByteUtils.getInt2R(data, 4) - 2730) / 10.0
                bms.temp3 = (ByteUtils.getInt2R(data, 6) - 2730) / 10.0
                bms.temp4 = (ByteUtils.getInt2R(data, 8) - 2730) / 10.0
                bms.temp5 = (ByteUtils.getInt2R(data, 10) - 2730) / 10.0
                bms.temp6 = (ByteUtils.getInt2R(data, 12) - 2730) / 10.0
                bms.tempMos = (ByteUtils.getInt2R(data, 14) - 2730) / 10.0
            }
            0x02, 0x03, 0x04, 0x05 -> {
                // Cell voltages (7 cells per packet)
                val startCell = (pNum - 2) * 7
                for (i in 0 until 7) {
                    val cellIndex = startCell + i
                    if (cellIndex < bms.cells.size) {
                        bms.cells[cellIndex] = ByteUtils.getInt2R(data, 2 + i * 2) / 1000.0
                    }
                }
            }
            0x06 -> {
                // Last cells and MOS environment temp
                bms.cells[28] = ByteUtils.getInt2R(data, 2) / 1000.0
                bms.cells[29] = ByteUtils.getInt2R(data, 4) / 1000.0
                bms.tempMosEnv = (ByteUtils.getInt2R(data, 10) - 2730) / 10.0

                // Calculate cell statistics
                updateBmsCellStats(bms)
            }
            0xD0 -> {
                // Extended BMS packet for F-series
                processExtendedBmsPacket(data, bms)
            }
        }

        return currentState
    }

    private fun processExtendedBmsPacket(data: ByteArray, bms: SmartBms) {
        val cells = data[21].toInt() and 0xFF

        // Read cell voltages
        for (i in 0 until cells) {
            bms.cells[i] = ByteUtils.getInt2R(data, 22 + i * 2) / 1000.0
        }

        val offset = 23 + cells * 2
        val nTemp = data[offset - 1].toInt() and 0xFF

        // Read temperatures
        bms.temp1 = (ByteUtils.getInt2R(data, offset) - 2730) / 10.0
        bms.temp2 = (ByteUtils.getInt2R(data, offset + 2) - 2730) / 10.0
        bms.temp3 = (ByteUtils.getInt2R(data, offset + 4) - 2730) / 10.0
        bms.temp4 = (ByteUtils.getInt2R(data, offset + 6) - 2730) / 10.0
        bms.temp5 = (ByteUtils.getInt2R(data, offset + 8) - 2730) / 10.0
        bms.temp6 = (ByteUtils.getInt2R(data, offset + 10) - 2730) / 10.0
        bms.tempMos = (ByteUtils.getInt2R(data, offset + 12) - 2730) / 10.0
        bms.tempMosEnv = (ByteUtils.getInt2R(data, offset + 14) - 2730) / 10.0

        val offset2 = offset + nTemp * 2
        bms.current = ByteUtils.getInt2R(data, offset2) / 100.0
        bms.voltage = ByteUtils.getInt2R(data, offset2 + 2) / 100.0
        bms.remPerc = ByteUtils.getInt2R(data, offset2 + 4) / 10
        bms.fullCycles = ByteUtils.getInt2R(data, offset2 + 9)
        bms.factoryCap = ByteUtils.getInt2R(data, offset2 + 11) * 10
        bms.remCap = bms.remPerc * bms.factoryCap / 100
        bms.temp1Env = ByteUtils.getInt2R(data, offset2 + 14) / 10.0
        bms.temp2Env = ByteUtils.getInt2R(data, offset2 + 16) / 10.0
        bms.humidity1Env = ByteUtils.getInt2R(data, offset2 + 18) / 10.0
        bms.humidity2Env = ByteUtils.getInt2R(data, offset2 + 20) / 10.0

        // Calculate cell statistics
        updateBmsCellStats(bms, cells)
    }

    /**
     * Frame 0xE1/0xE2: BMS Serial Number
     */
    private fun processBmsSerial(data: ByteArray, frameType: Int): WheelState? {
        val bmsNum = (frameType and 0xFF) - 0xE0
        val bms = if (bmsNum == 1) bms1 else bms2

        val snData = ByteArray(18)
        data.copyInto(snData, 0, 2, 16)
        data.copyInto(snData, 14, 17, 20)
        snData[17] = 0

        bms.serialNumber = snData.decodeToString().trim { it <= ' ' || it == '\u0000' }
        return null
    }

    /**
     * Frame 0xE5/0xE6: BMS Firmware Version
     */
    private fun processBmsFirmware(data: ByteArray, frameType: Int): WheelState? {
        val bmsNum = (frameType and 0xFF) - 0xE4
        val bms = if (bmsNum == 1) bms1 else bms2

        val snData = ByteArray(19)
        data.copyInto(snData, 0, 2, 16)
        data.copyInto(snData, 14, 17, 20)
        snData[18] = 0

        bms.versionNumber = snData.decodeToString().trim { it <= ' ' || it == '\u0000' }
        return null
    }

    private fun updateBmsCellStats(bms: SmartBms, cellCount: Int = getCellsForWheel()) {
        bms.minCell = bms.cells[0]
        bms.maxCell = bms.cells[0]
        bms.maxCellNum = 1
        bms.minCellNum = 1
        var totalVolt = 0.0

        for (i in 0 until cellCount) {
            val cell = bms.cells[i]
            if (cell > 0.0) {
                totalVolt += cell
                if (bms.maxCell < cell) {
                    bms.maxCell = cell
                    bms.maxCellNum = i + 1
                }
                if (bms.minCell > cell) {
                    bms.minCell = cell
                    bms.minCellNum = i + 1
                }
            }
        }
        bms.cellDiff = bms.maxCell - bms.minCell
        bms.avgCell = totalVolt / cellCount
    }

    private fun calculateBatteryPercent(voltage: Int, useBetterPercents: Boolean): Int {
        return when {
            is84vWheel() -> calc84vBattery(voltage, useBetterPercents)
            is126vWheel() -> calc126vBattery(voltage, useBetterPercents)
            is151vWheel() -> calc151vBattery(voltage, useBetterPercents)
            is176vWheel() -> calc176vBattery(voltage, useBetterPercents)
            is100vWheel() -> calc100vBattery(voltage, useBetterPercents)
            else -> calc67vBattery(voltage, useBetterPercents)
        }
    }

    private fun calc84vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 8350 -> 100
                voltage > 6800 -> (voltage - 6650) / 17
                voltage > 6400 -> (voltage - 6400) / 45
                else -> 0
            }
        } else {
            when {
                voltage < 6250 -> 0
                voltage >= 8250 -> 100
                else -> (voltage - 6250) / 20
            }
        }
    }

    private fun calc126vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 12525 -> 100
                voltage > 10200 -> ((voltage - 9975) / 25.5).roundToInt()
                voltage > 9600 -> ((voltage - 9600) / 67.5).roundToInt()
                else -> 0
            }
        } else {
            when {
                voltage < 9375 -> 0
                voltage >= 12375 -> 100
                else -> (voltage - 9375) / 30
            }
        }
    }

    private fun calc151vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 15030 -> 100
                voltage > 12240 -> ((voltage - 11970) / 30.6).roundToInt()
                voltage > 11520 -> ((voltage - 11520) / 81.0).roundToInt()
                else -> 0
            }
        } else {
            when {
                voltage < 11250 -> 0
                voltage >= 14850 -> 100
                else -> (voltage - 11250) / 36
            }
        }
    }

    private fun calc176vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 17535 -> 100
                voltage > 14280 -> ((voltage - 13965) / 35.7).roundToInt()
                voltage > 13440 -> ((voltage - 13440) / 94.5).roundToInt()
                else -> 0
            }
        } else {
            when {
                voltage < 13125 -> 0
                voltage >= 17325 -> 100
                else -> (voltage - 13125) / 42
            }
        }
    }

    private fun calc100vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 10020 -> 100
                voltage > 8160 -> ((voltage - 7980) / 20.4).roundToInt()
                voltage > 7680 -> ((voltage - 7680) / 54.0).roundToInt()
                else -> 0
            }
        } else {
            when {
                voltage < 7500 -> 0
                voltage >= 9900 -> 100
                else -> (voltage - 7500) / 24
            }
        }
    }

    private fun calc67vBattery(voltage: Int, useBetterPercents: Boolean): Int {
        return if (useBetterPercents) {
            when {
                voltage > 6680 -> 100
                voltage > 5440 -> ((voltage - 5320) / 13.6).roundToInt()
                voltage > 5120 -> (voltage - 5120) / 36
                else -> 0
            }
        } else {
            when {
                voltage < 5000 -> 0
                voltage >= 6600 -> 100
                else -> (voltage - 5000) / 16
            }
        }
    }

    private fun is84vWheel(): Boolean {
        return model in listOf("KS-18L", "KS-16X", "KS-16XF", "RW", "KS-18LH", "KS-18LY", "KS-S18", "KS-S16", "KS-S16P") ||
                name.startsWith("ROCKW")
    }

    private fun is126vWheel(): Boolean = model in listOf("KS-S20", "KS-S22")
    private fun is151vWheel(): Boolean = model in listOf("KS-F18P")
    private fun is176vWheel(): Boolean = model in listOf("KS-F22P")
    private fun is100vWheel(): Boolean = model in listOf("KS-S19")

    private fun getCellsForWheel(): Int {
        return when {
            is84vWheel() -> 20
            is100vWheel() -> 24
            is126vWheel() -> 30
            is151vWheel() -> 36
            is176vWheel() -> 42
            else -> 16
        }
    }

    private fun createRequest(type: Int): ByteArray {
        return byteArrayOf(
            0xAA.toByte(), 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            type.toByte(), 0x14, 0x5A, 0x5A
        )
    }

    override fun isReady(): Boolean = stateLock.withLock {
        model.isNotEmpty() && bms1.voltage > 0
    }

    override fun reset() = stateLock.withLock {
        ksAlarm1Speed = 0
        ksAlarm2Speed = 0
        ksAlarm3Speed = 0
        wheelMaxSpeed = 0
        m18Lkm = true
        mMode = 0
        mSpeedLimit = 0.0
        model = ""
        name = ""
        serialNumber = ""
        version = ""
        bms1 = SmartBms()
        bms2 = SmartBms()
    }

    override fun buildCommand(command: WheelCommand): List<WheelCommand> {
        return when (command) {
            is WheelCommand.Beep -> {
                val data = byteArrayOf(
                    0xAA.toByte(), 0x55, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x88.toByte(), 0x14, 0x5A, 0x5A
                )
                listOf(WheelCommand.SendBytes(data))
            }
            is WheelCommand.SetLight -> {
                val mode: Byte = if (command.enabled) 0x12 else 0x13
                val enable: Byte = 0x01
                val data = byteArrayOf(
                    0xAA.toByte(), 0x55, mode, enable, 0x00, 0x00, 0x00, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x73, 0x14, 0x5A, 0x5A
                )
                listOf(WheelCommand.SendBytes(data))
            }
            else -> emptyList()
        }
    }

    override fun getInitCommands(): List<WheelCommand> {
        return listOf(
            WheelCommand.SendBytes(createRequest(0x9B)), // Request name
            WheelCommand.SendDelayed(createRequest(0x63), 100), // Request serial
            WheelCommand.SendDelayed(createRequest(0x98), 200)  // Request alarm settings
        )
    }

    companion object {
        private const val KS18L_SCALER = 0.83
    }
}
