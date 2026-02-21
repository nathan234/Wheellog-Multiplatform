package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.SmartBms
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.utils.ByteUtils
import com.cooper.wheellog.core.utils.Lock
import com.cooper.wheellog.core.utils.withLock
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Gotway/Begode protocol decoder.
 *
 * Supports multiple firmware variants:
 * - Begode (standard GW firmware)
 * - ExtremeBull
 * - Freestyl3r (custom firmware with hardware PWM)
 * - SmirnoV/SV (Alexovik custom firmware)
 *
 * Frame format (reassembled by GotwayUnpacker):
 * - Bytes 0-1:  Header (55 AA)
 * - Bytes 2-3:  Voltage (BE)
 * - Bytes 4-5:  Speed (BE, signed)
 * - Bytes 6-9:  Distance (BE)
 * - Bytes 10-11: Current (BE, signed)
 * - Bytes 12-13: Temperature (BE)
 * - Byte 18:    Frame type
 * - Byte 19:    Padding (5A 5A)
 *
 * Frame types:
 *   0x00 = Live telemetry (speed, voltage, current, temp, distance)
 *   0x01 = Extended data (true voltage, BMS temps)
 *   0x02/0x03 = BMS cell voltages
 *   0x04 = Total distance, settings, alerts
 *   0x07 = Battery current, motor temperature
 *   0xFF = Firmware settings
 *
 * Init commands: "V" (firmware), "b", "N" (name), "b"
 * Retry: re-sends "V"/"N" via getKeepAliveCommand until both fw and model
 * are populated (max 50 attempts).
 *
 * This class is thread-safe.
 */
class GotwayDecoder : WheelDecoder {

    override val wheelType: WheelType = WheelType.GOTWAY
    private val stateLock = Lock()

    private val unpacker = GotwayUnpacker()
    private var model = ""
    private var imu = ""
    private var fw = ""
    private var fwProt = ""
    private var smartBmsCells = 0
    private var trueVoltage = false
    private var trueCurrent = false
    private var bmsCurrent = false
    private var truePWM = false
    private var isReady = false
    private var hasReceivedData = false

    // Retry counter for firmware/model info requests (mirrors legacy adapter)
    private var infoAttempt = 0
    private companion object {
        private const val MAX_INFO_ATTEMPTS = 50
        private const val RATIO_GW = 0.875

        // Frame types (byte 18 of unpacked frame)
        private const val FRAME_LIVE_DATA = 0x00
        private const val FRAME_EXTENDED = 0x01
        private const val FRAME_BMS_CELLS_1 = 0x02
        private const val FRAME_BMS_CELLS_2 = 0x03
        private const val FRAME_TOTAL_DISTANCE = 0x04
        private const val FRAME_CURRENT_TEMP = 0x07
        private const val FRAME_SETTINGS = 0xFF
    }

    // BMS state (mutable during decode)
    private var bms1 = SmartBms()
    private var bms2 = SmartBms()

    override fun decode(data: ByteArray, currentState: WheelState, config: DecoderConfig): DecodedData? {
        return stateLock.withLock {
            var newState = currentState
            var hasNewData = false
            val commands = mutableListOf<WheelCommand>()
            var news: String? = null

            // Try to parse firmware/model info from string data
            if (model.isEmpty() || fw.isEmpty()) {
                val dataStr = data.decodeToString().trim()
                when {
                    dataStr.startsWith("NAME") -> {
                        model = dataStr.substring(5).trim()
                        newState = newState.copy(model = model)
                    }
                    dataStr.startsWith("GW") -> {
                        fw = dataStr.substring(2).trim()
                        fwProt = "Begode"
                        isReady = true
                        newState = newState.copy(version = fw)
                    }
                    dataStr.startsWith("JN") -> {
                        fw = dataStr.substring(2).trim()
                        fwProt = "ExtremeBull"
                        isReady = true
                        newState = newState.copy(version = fw)
                    }
                    dataStr.startsWith("CF") -> {
                        fw = dataStr.substring(2).trim()
                        fwProt = "Freestyl3r"
                        isReady = true
                        newState = newState.copy(version = fw)
                    }
                    dataStr.startsWith("BF") -> {
                        fw = dataStr.substring(2).trim()
                        fwProt = "SV"
                        isReady = true
                        newState = newState.copy(version = fw)
                    }
                    dataStr.startsWith("MPU") -> {
                        imu = dataStr.substring(1, minOf(7, dataStr.length)).trim()
                    }
                }
            }

            // Process each byte through the unpacker
            for (byte in data) {
                if (unpacker.addChar(byte.toInt() and 0xFF)) {
                    val buff = unpacker.getBuffer()
                    val result = processFrame(buff, newState, config)
                    if (result != null) {
                        newState = result.state
                        hasNewData = hasNewData || result.hasNewData
                        result.news?.let { news = it }
                        commands.addAll(result.commands)
                    }
                }
            }

            // Retry firmware/model requests until both are populated (like legacy adapter)
            if (hasNewData && (fw.isEmpty() || model.isEmpty())) {
                if (infoAttempt < MAX_INFO_ATTEMPTS) {
                    infoAttempt++
                    if (fw.isEmpty()) {
                        commands.add(WheelCommand.SendBytes("V".encodeToByteArray()))
                    } else if (model.isEmpty()) {
                        commands.add(WheelCommand.SendBytes("N".encodeToByteArray()))
                    }
                } else {
                    // Fallback after max attempts
                    if (model.isEmpty()) {
                        model = fwProt.ifEmpty { "Begode" }
                        newState = newState.copy(model = model)
                    }
                    if (fw.isEmpty()) {
                        fw = "-"
                        newState = newState.copy(version = fw)
                        isReady = true
                    }
                }
            }

            if (hasNewData || newState != currentState) {
                DecodedData(
                    newState = newState.copy(
                        bms1 = bms1.toSnapshot(),
                        bms2 = bms2.toSnapshot()
                    ),
                    commands = commands,
                    hasNewData = hasNewData,
                    news = news
                )
            } else null
        }
    }

    private data class FrameResult(
        val state: WheelState,
        val hasNewData: Boolean,
        val news: String? = null,
        val commands: List<WheelCommand> = emptyList()
    )

    private fun processFrame(
        buff: ByteArray,
        currentState: WheelState,
        config: DecoderConfig
    ): FrameResult? {
        if (buff.size < 20) return null

        val frameType = buff[18].toInt() and 0xFF
        val isAlexovikFW = fwProt == "SV"
        val gotwayNegative = config.gotwayNegative

        return when (frameType) {
            FRAME_LIVE_DATA -> processLiveDataFrame(buff, currentState, config, isAlexovikFW, gotwayNegative)
            FRAME_EXTENDED -> processExtendedFrame(buff, currentState, isAlexovikFW)
            FRAME_BMS_CELLS_1, FRAME_BMS_CELLS_2 -> processBmsCellsFrame(buff, frameType)
            FRAME_TOTAL_DISTANCE -> processTotalDistanceFrame(buff, currentState, config, isAlexovikFW)
            FRAME_CURRENT_TEMP -> processCurrentTempFrame(buff, currentState, isAlexovikFW, gotwayNegative)
            FRAME_SETTINGS -> processSettingsFrame(buff, currentState)
            else -> null
        }
    }

    /**
     * Frame type 0x00: Live telemetry data
     */
    private fun processLiveDataFrame(
        buff: ByteArray,
        currentState: WheelState,
        config: DecoderConfig,
        isAlexovikFW: Boolean,
        gotwayNegative: Int
    ): FrameResult {
        var voltage = ByteUtils.shortFromBytesBE(buff, 2)
        var speed = (ByteUtils.signedShortFromBytesBE(buff, 4) * 3.6).roundToInt()
        var distance = 0L

        if (!isAlexovikFW) {
            distance = ByteUtils.shortFromBytesBE(buff, 8).toLong()
        } else {
            // SmirnoV protocol: battery current in different location
            if ((buff[7].toInt() and 0x01) == 1) {
                val batteryCurrent = ByteUtils.signedShortFromBytesBE(buff, 8)
                trueCurrent = true
                // Would set current here
            }
        }

        var phaseCurrent = ByteUtils.signedShortFromBytesBE(buff, 10)

        val temperature = if (!isAlexovikFW) {
            // MPU6050 temperature formula
            ((ByteUtils.signedShortFromBytesBE(buff, 12).toFloat() / 340.0f) + 36.53f) * 100
        } else {
            // MPU6500 temperature formula
            ((ByteUtils.signedShortFromBytesBE(buff, 12).toFloat() / 333.87f) + 21.0f) * 100
        }.roundToInt()

        var hwPwm = ByteUtils.signedShortFromBytesBE(buff, 14) * 10

        // Apply direction/polarity settings
        if (gotwayNegative == 0) {
            speed = abs(speed)
            phaseCurrent = abs(phaseCurrent)
            hwPwm = abs(hwPwm)
        } else {
            phaseCurrent *= gotwayNegative
            if (!isAlexovikFW) {
                speed *= gotwayNegative
                hwPwm *= gotwayNegative
            }
        }

        // Calculate battery percentage
        val battery = if (config.useCustomPercents) {
            calculateBetterPercent(voltage)
        } else {
            calculateStandardPercent(voltage)
        }

        // Apply ratio if configured (some boards report inflated values)
        if (config.useRatio) {
            speed = (speed * RATIO_GW).roundToInt()
            distance = (distance * RATIO_GW).roundToInt().toLong()
        }

        // Normalize to metric when wheel reports in miles
        if (currentState.inMiles) {
            speed = (speed / ByteUtils.KM_TO_MILES_MULTIPLIER).roundToInt()
            distance = (distance / ByteUtils.KM_TO_MILES_MULTIPLIER).roundToLong()
        }

        // Scale voltage based on wheel configuration
        voltage = scaleVoltage(voltage, config).roundToInt()

        // Track that we've received valid live data (for isReady check)
        if (voltage > 0) {
            hasReceivedData = true
        }

        // Calculate current and power
        val calculatedPwm = hwPwm / 10000.0
        val current = if (!trueCurrent || !bmsCurrent) {
            (calculatedPwm * phaseCurrent).roundToInt()
        } else {
            currentState.current
        }
        val power = ((current / 100.0) * voltage).roundToInt()

        val newState = currentState.copy(
            speed = speed,
            voltage = if (!trueVoltage) voltage else currentState.voltage,
            phaseCurrent = phaseCurrent,
            current = current,
            power = power,
            temperature = temperature,
            wheelDistance = distance,
            batteryLevel = battery,
            output = if (!truePWM) hwPwm else currentState.output,
            calculatedPwm = if (!truePWM) calculatedPwm else currentState.calculatedPwm,
            wheelType = WheelType.GOTWAY,
            model = model.ifEmpty { currentState.model }
        )

        val hasNewData = !((trueVoltage) || trueCurrent || bmsCurrent) || isAlexovikFW

        return FrameResult(newState, hasNewData)
    }

    /**
     * Frame type 0x01: Extended data (voltage, BMS temps)
     */
    private fun processExtendedFrame(
        buff: ByteArray,
        currentState: WheelState,
        isAlexovikFW: Boolean
    ): FrameResult? {
        if (isAlexovikFW) return null

        trueVoltage = true
        val batVoltage = ByteUtils.shortFromBytesBE(buff, 6)
        val bmsNum = buff[19].toInt() and 0xFF
        val bms = if (bmsNum < 2) bms1 else bms2

        val bmsCurrentVal = ByteUtils.signedShortFromBytesBE(buff, 8)
        bms.current = bmsCurrentVal / 10.0

        if (bmsCurrentVal > 0) bmsCurrent = false

        if (bmsNum % 2 == 0) {
            bms.temp1 = ByteUtils.signedShortFromBytesBE(buff, 10).toDouble()
            bms.temp2 = ByteUtils.signedShortFromBytesBE(buff, 12).toDouble()
            bms.semiVoltage1 = ByteUtils.signedShortFromBytesBE(buff, 14) / 10.0
        } else {
            bms.temp3 = ByteUtils.signedShortFromBytesBE(buff, 10).toDouble()
            bms.temp4 = ByteUtils.signedShortFromBytesBE(buff, 12).toDouble()
            bms.semiVoltage2 = ByteUtils.signedShortFromBytesBE(buff, 14) / 10.0
        }

        val newState = currentState.copy(
            voltage = batVoltage * 10
        )

        return FrameResult(newState, bmsCurrent || (!trueCurrent && trueVoltage))
    }

    /**
     * Frame type 0x02/0x03: BMS cell voltages
     */
    private fun processBmsCellsFrame(buff: ByteArray, frameType: Int): FrameResult? {
        val bmsNum = (frameType and 0xFF) - 0x01
        val bms = if (bmsNum == 1) bms1 else bms2
        val pNum = buff[19].toInt() and 0xFF

        for (i in 0 until 8) {
            val cellNum = i + pNum * 8
            val cellVal = ByteUtils.shortFromBytesBE(buff, (i + 1) * 2) / 1000.0
            if (cellNum < bms.cells.size) {
                bms.cells[cellNum] = cellVal
            }
            if (smartBmsCells <= cellNum && cellVal != 0.0) {
                smartBmsCells = cellNum + 1
            } else if (smartBmsCells == cellNum + 1 && bms.cellNum != smartBmsCells) {
                bms.cellNum = smartBmsCells
            }
        }

        // Recalculate cell stats
        updateBmsCellStats(bms)

        return null // BMS data doesn't trigger new data event
    }

    /**
     * Frame type 0x04: Total distance and settings
     */
    private fun processTotalDistanceFrame(
        buff: ByteArray,
        currentState: WheelState,
        config: DecoderConfig,
        isAlexovikFW: Boolean
    ): FrameResult {
        var totalDistance = ByteUtils.getInt4(buff, 2)
        if (config.useRatio) {
            totalDistance = (totalDistance * RATIO_GW).roundToInt().toLong()
        }

        var news: String? = null

        if (!isAlexovikFW) {
            val settings = ByteUtils.shortFromBytesBE(buff, 6)
            val pedalsMode = (settings shr 13) and 0x03
            val speedAlarms = (settings shr 10) and 0x03
            val rollAngle = (settings shr 7) and 0x03
            val inMiles = settings and 0x01
            val powerOffTime = ByteUtils.shortFromBytesBE(buff, 8)
            var tiltBackSpeed = ByteUtils.shortFromBytesBE(buff, 10)
            if (tiltBackSpeed >= 100) tiltBackSpeed = 0
            val alert = buff[14].toInt() and 0xFF
            val ledMode = buff[13].toInt() and 0xFF
            val lightMode = buff[15].toInt() and 0x03

            // Build alert string
            val alertBuilder = StringBuilder()
            val wheelAlarm = (alert and 0x01) == 1
            if ((alert shr 1) and 0x01 == 1) alertBuilder.append("Speed2 ")
            if ((alert shr 2) and 0x01 == 1) alertBuilder.append("Speed1 ")
            if ((alert shr 3) and 0x01 == 1) alertBuilder.append("LowVoltage ")
            if ((alert shr 4) and 0x01 == 1) alertBuilder.append("OverVoltage ")
            if ((alert shr 5) and 0x01 == 1) alertBuilder.append("OverTemperature ")
            if ((alert shr 6) and 0x01 == 1) alertBuilder.append("errHallSensors ")
            if ((alert shr 7) and 0x01 == 1) alertBuilder.append("TransportMode")

            val alertLine = alertBuilder.toString().trim()
            if (alertLine.isNotEmpty()) {
                news = alertLine
            }

            // Normalize to metric when wheel reports in miles
            val isMiles = inMiles == 1
            if (isMiles) {
                totalDistance = (totalDistance / ByteUtils.KM_TO_MILES_MULTIPLIER).roundToLong()
            }

            return FrameResult(
                state = currentState.copy(
                    totalDistance = totalDistance,
                    pedalsMode = 2 - pedalsMode,
                    speedAlarms = speedAlarms,
                    rollAngle = rollAngle,
                    tiltBackSpeed = tiltBackSpeed,
                    lightMode = lightMode,
                    ledMode = ledMode,
                    wheelAlarm = wheelAlarm,
                    alert = alertLine,
                    inMiles = isMiles
                ),
                hasNewData = false,
                news = news
            )
        }

        return FrameResult(
            state = currentState.copy(totalDistance = totalDistance),
            hasNewData = false
        )
    }

    /**
     * Frame type 0x07: Current and motor temperature
     */
    private fun processCurrentTempFrame(
        buff: ByteArray,
        currentState: WheelState,
        isAlexovikFW: Boolean,
        gotwayNegative: Int
    ): FrameResult? {
        if (isAlexovikFW) return null

        trueCurrent = true
        val batteryCurrent = ByteUtils.signedShortFromBytesBE(buff, 2)
        val motorTemp = ByteUtils.signedShortFromBytesBE(buff, 6)
        var hwPWMb = ByteUtils.signedShortFromBytesBE(buff, 8)

        if (hwPWMb > 0) {
            truePWM = true
        }

        if (truePWM) {
            hwPWMb = if (gotwayNegative == 0) {
                abs(hwPWMb)
            } else {
                hwPWMb * gotwayNegative * (-1)
            }
        }

        val current = if (!bmsCurrent) (-1) * batteryCurrent else currentState.current
        val output = if (truePWM) hwPWMb * 100 else currentState.output
        val calculatedPwm = if (truePWM) output / 10000.0 else currentState.calculatedPwm

        return FrameResult(
            state = currentState.copy(
                current = current,
                temperature2 = motorTemp * 100,
                output = output,
                calculatedPwm = calculatedPwm
            ),
            hasNewData = trueCurrent && !bmsCurrent
        )
    }

    /**
     * Frame type 0xFF: Firmware settings (Alexovik/SmirnoV custom firmware)
     *
     * Layout:
     * - Byte 2 bit 0: extreme mode
     * - Byte 3: braking current
     * - Byte 4 bit 0: rotation control enabled
     * - Byte 5: cutout angle raw (0-100, display = raw + 260)
     * - Bytes 6-17: PID tuning parameters (not stored in WheelState)
     */
    private fun processSettingsFrame(buff: ByteArray, currentState: WheelState): FrameResult {
        val cutoutAngle = (buff[5].toInt() and 0xFF) + 260
        return FrameResult(
            state = currentState.copy(cutoutAngle = cutoutAngle),
            hasNewData = false
        )
    }

    private fun updateBmsCellStats(bms: SmartBms) {
        if (smartBmsCells == 0) return

        bms.minCell = bms.cells[0]
        bms.maxCell = bms.cells[0]
        bms.maxCellNum = 1
        bms.minCellNum = 1
        var totalVolt = 0.0

        for (i in 0 until smartBmsCells) {
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
        bms.avgCell = totalVolt / smartBmsCells
        bms.voltage = totalVolt
    }

    private fun calculateBetterPercent(voltage: Int): Int {
        return when {
            voltage > 6680 -> 100
            voltage > 5440 -> ((voltage - 5320) / 13.6).roundToInt()
            voltage > 5120 -> (voltage - 5120) / 36
            else -> 0
        }
    }

    private fun calculateStandardPercent(voltage: Int): Int {
        return when {
            voltage <= 5290 -> 0
            voltage >= 6580 -> 100
            else -> (voltage - 5290) / 13
        }
    }

    private fun scaleVoltage(voltage: Int, config: DecoderConfig): Double {
        val scaler = when (config.gotwayVoltage) {
            0 -> 1.0                    // 67.2V (16S)
            1 -> 1.25                   // 84V (20S)
            2 -> 1.5                    // 100.8V (24S)
            3 -> 1.7380952380952381     // 126V (28S)
            4 -> 2.0                    // 134.4V (32S)
            5 -> 2.5                    // 168V (40S)
            6 -> 2.25                   // 151V (36S)
            else -> 1.0
        }
        return voltage * scaler
    }

    override fun isReady(): Boolean = stateLock.withLock {
        isReady && hasReceivedData
    }

    override fun reset() {
        stateLock.withLock {
            unpacker.reset()
            model = ""
            imu = ""
            fw = ""
            fwProt = ""
            smartBmsCells = 0
            trueVoltage = false
            trueCurrent = false
            bmsCurrent = false
            truePWM = false
            isReady = false
            hasReceivedData = false
            infoAttempt = 0
            bms1 = SmartBms()
            bms2 = SmartBms()
        }
    }

    override fun buildCommand(command: WheelCommand): List<WheelCommand> {
        return when (command) {
            is WheelCommand.Beep -> listOf(
                WheelCommand.SendBytes("b".encodeToByteArray())
            )
            is WheelCommand.SetLight -> {
                val mode = if (command.enabled) 1 else 0
                buildCommand(WheelCommand.SetLightMode(mode))
            }
            is WheelCommand.SetLightMode -> {
                // 0=off("E"), 1=on("Q"), 2=strobe("T")
                val cmd = when (command.mode) {
                    1 -> "Q"
                    2 -> "T"
                    else -> "E"
                }
                listOf(WheelCommand.SendBytes(cmd.encodeToByteArray()))
            }
            is WheelCommand.SetPedalsMode -> {
                // 0=hard("h"), 1=fast("f"), 2=soft("s"), 3=intermediate("i")
                val cmd = when (command.mode) {
                    0 -> "h"
                    1 -> "f"
                    2 -> "s"
                    3 -> "i"
                    else -> return emptyList()
                }
                listOf(WheelCommand.SendBytes(cmd.encodeToByteArray()))
            }
            is WheelCommand.SetMilesMode -> {
                val cmd = if (command.enabled) "m" else "g"
                listOf(WheelCommand.SendBytes(cmd.encodeToByteArray()))
            }
            is WheelCommand.SetRollAngleMode -> {
                // 0=normal(">"), 1=equal("="), 2=reverse("<")
                val cmd = when (command.mode) {
                    0 -> ">"
                    1 -> "="
                    2 -> "<"
                    else -> return emptyList()
                }
                listOf(WheelCommand.SendBytes(cmd.encodeToByteArray()))
            }
            is WheelCommand.SetLedMode -> {
                // Multi-step: "W" then "M" after 100ms, then mode digit after 300ms then "b" after 100ms
                val param = byteArrayOf(((command.mode % 10) + 0x30).toByte())
                listOf(
                    WheelCommand.SendBytes("W".encodeToByteArray()),
                    WheelCommand.SendDelayed("M".encodeToByteArray(), 100),
                    WheelCommand.SendDelayed(param, 300),
                    WheelCommand.SendDelayed("b".encodeToByteArray(), 100)
                )
            }
            is WheelCommand.SetBeeperVolume -> {
                // Multi-step: "W" then "B" after 100ms, then volume digit after 300ms then "b" after 100ms
                val param = byteArrayOf(((command.volume % 10) + 0x30).toByte())
                listOf(
                    WheelCommand.SendBytes("W".encodeToByteArray()),
                    WheelCommand.SendDelayed("B".encodeToByteArray(), 100),
                    WheelCommand.SendDelayed(param, 300),
                    WheelCommand.SendDelayed("b".encodeToByteArray(), 100)
                )
            }
            is WheelCommand.SetCutoutAngle -> {
                // Angle is 260-360° display value; protocol sends raw 0-100 (value - 260)
                listOf(WheelCommand.SendBytes(byteArrayOf(0x72, 0x73, (command.angle - 260).toByte())))
            }
            is WheelCommand.SetAlarmMode -> {
                // 0=two alarms("o"), 1=one alarm("u"), 2=off("i"), 3=CF tiltback("I")
                val cmd = when (command.mode) {
                    0 -> "o"
                    1 -> "u"
                    2 -> "i"
                    3 -> "I"
                    else -> return emptyList()
                }
                listOf(WheelCommand.SendBytes(cmd.encodeToByteArray()))
            }
            is WheelCommand.Calibrate -> {
                listOf(
                    WheelCommand.SendBytes("c".encodeToByteArray()),
                    WheelCommand.SendDelayed("y".encodeToByteArray(), 300)
                )
            }
            is WheelCommand.SetMaxSpeed -> {
                if (command.speed != 0) {
                    val hhh = byteArrayOf(((command.speed / 10) + 0x30).toByte())
                    val lll = byteArrayOf(((command.speed % 10) + 0x30).toByte())
                    listOf(
                        WheelCommand.SendBytes("b".encodeToByteArray()),
                        WheelCommand.SendDelayed("W".encodeToByteArray(), 100),
                        WheelCommand.SendDelayed("Y".encodeToByteArray(), 100),
                        WheelCommand.SendDelayed(hhh, 200),
                        WheelCommand.SendDelayed(lll, 100),
                        WheelCommand.SendDelayed("b".encodeToByteArray(), 200),
                        WheelCommand.SendDelayed("b".encodeToByteArray(), 100)
                    )
                } else {
                    listOf(
                        WheelCommand.SendBytes("b".encodeToByteArray()),
                        WheelCommand.SendDelayed("\"".encodeToByteArray(), 100),
                        WheelCommand.SendDelayed("b".encodeToByteArray(), 200),
                        WheelCommand.SendDelayed("b".encodeToByteArray(), 100)
                    )
                }
            }
            else -> emptyList()
        }
    }

    override fun getInitCommands(): List<WheelCommand> {
        // Request firmware version and name
        return listOf(
            WheelCommand.SendBytes("V".encodeToByteArray()),
            WheelCommand.SendDelayed("b".encodeToByteArray(), 100),
            WheelCommand.SendDelayed("N".encodeToByteArray(), 200),
            WheelCommand.SendDelayed("b".encodeToByteArray(), 300)
        )
    }

}
