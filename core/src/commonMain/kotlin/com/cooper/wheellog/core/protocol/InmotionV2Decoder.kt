package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.util.ByteUtils
import kotlin.math.roundToInt

/**
 * InMotion V2 protocol decoder.
 *
 * Supports InMotion wheels with V2 BLE protocol:
 * - V11, V11Y
 * - V12HS, V12HT, V12PRO, V12S
 * - V13, V13PRO
 * - V14g, V14s
 * - V9
 *
 * Frame format:
 * - Header: AA AA
 * - Flags: 0x11 (Initial) or 0x14 (Default)
 * - Length: Data length + 1
 * - Command: Command byte (masked with 0x7F)
 * - Data: Variable length payload
 * - Checksum: XOR of all bytes from flags to end of data
 */
class InmotionV2Decoder : WheelDecoder {

    override val wheelType: WheelType = WheelType.INMOTION_V2
    override val keepAliveIntervalMs: Long = 25L

    private val unpacker = InmotionV2Unpacker()
    private var model = Model.UNKNOWN
    private var protoVer = 0
    private var serialNumber = ""
    private var version = ""
    private var isModelDetected = false

    /**
     * InMotion V2 wheel models.
     */
    enum class Model(val id: Int, val displayName: String, val maxSpeed: Int, val cellCount: Int) {
        V11(61, "Inmotion V11", 60, 20),
        V11Y(62, "Inmotion V11y", 120, 20),
        V12HS(71, "Inmotion V12 HS", 70, 24),
        V12HT(72, "Inmotion V12 HT", 70, 24),
        V12PRO(73, "Inmotion V12 PRO", 70, 24),
        V13(81, "Inmotion V13", 120, 30),
        V13PRO(82, "Inmotion V13 PRO", 120, 30),
        V14g(91, "Inmotion V14 50GB", 120, 32),
        V14s(92, "Inmotion V14 50S", 120, 32),
        V12S(111, "Inmotion V12S", 120, 20),
        V9(121, "Inmotion V9", 120, 20),
        UNKNOWN(0, "Inmotion Unknown", 100, 20);

        companion object {
            /**
             * Find model by series and type IDs.
             * The full ID is series * 10 + type.
             */
            fun findById(series: Int, type: Int): Model {
                val fullId = series * 10 + type
                return entries.find { it.id == fullId } ?: UNKNOWN
            }
        }
    }

    /**
     * Message flags indicating the type of message.
     */
    object Flag {
        const val INITIAL = 0x11
        const val DEFAULT = 0x14
    }

    /**
     * Command types for V2 protocol.
     */
    object Command {
        const val MAIN_VERSION = 0x01
        const val MAIN_INFO = 0x02
        const val DIAGNOSTIC = 0x03
        const val REAL_TIME_INFO = 0x04
        const val BATTERY_REAL_TIME_INFO = 0x05
        const val SOMETHING1 = 0x10
        const val TOTAL_STATS = 0x11
        const val SETTINGS = 0x20
        const val CONTROL = 0x60
    }

    override fun decode(data: ByteArray, currentState: WheelState, config: DecoderConfig): DecodedData? {
        var newState = currentState
        var hasNewData = false
        var news: String? = null

        for (byte in data) {
            if (unpacker.addChar(byte.toInt() and 0xFF)) {
                val buffer = unpacker.getBuffer()
                val message = verifyAndParse(buffer)

                if (message != null) {
                    val result = processMessage(message, newState)
                    if (result != null) {
                        newState = result.state
                        hasNewData = hasNewData || result.hasNewData
                        result.news?.let { news = it }
                    }
                }
            }
        }

        return if (hasNewData || newState != currentState) {
            DecodedData(
                newState = newState,
                hasNewData = hasNewData,
                news = news
            )
        } else null
    }

    /**
     * Parsed message from the wheel.
     */
    private data class Message(
        val flags: Int,
        val len: Int,
        val command: Int,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Message) return false
            return flags == other.flags && len == other.len &&
                    command == other.command && data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = flags
            result = 31 * result + len
            result = 31 * result + command
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Result from processing a message.
     */
    private data class ProcessResult(
        val state: WheelState,
        val hasNewData: Boolean,
        val news: String? = null
    )

    /**
     * Verify checksum and parse message from buffer.
     */
    private fun verifyAndParse(buffer: ByteArray): Message? {
        if (buffer.size < 5) return null

        // Calculate checksum (XOR of all bytes except header and checksum)
        val dataBuffer = buffer.copyOfRange(2, buffer.size - 1)
        var check = 0
        for (byte in dataBuffer) {
            check = (check xor (byte.toInt() and 0xFF)) and 0xFF
        }

        val bufferCheck = buffer[buffer.size - 1].toInt() and 0xFF
        if (check != bufferCheck) {
            return null
        }

        val flags = buffer[2].toInt() and 0xFF
        val len = buffer[3].toInt() and 0xFF
        val command = buffer[4].toInt() and 0x7F

        val messageData = if (len > 1 && buffer.size > 5) {
            buffer.copyOfRange(5, minOf(5 + len - 1, buffer.size))
        } else {
            ByteArray(0)
        }

        return Message(flags, len, command, messageData)
    }

    /**
     * Process a verified message and update state.
     */
    private fun processMessage(message: Message, currentState: WheelState): ProcessResult? {
        return when {
            message.flags == Flag.INITIAL -> {
                when (message.command) {
                    Command.MAIN_INFO -> processMainInfo(message, currentState)
                    else -> null
                }
            }
            message.flags == Flag.DEFAULT -> {
                when (message.command) {
                    Command.SETTINGS -> processSettings(message, currentState)
                    Command.DIAGNOSTIC -> processDiagnostic(message, currentState)
                    Command.BATTERY_REAL_TIME_INFO -> processBatteryRealTimeInfo(message, currentState)
                    Command.TOTAL_STATS -> processTotalStats(message, currentState)
                    Command.REAL_TIME_INFO -> processRealTimeInfo(message, currentState)
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Process main info (car type, serial number, versions).
     */
    private fun processMainInfo(message: Message, currentState: WheelState): ProcessResult? {
        val data = message.data
        if (data.isEmpty()) return null

        var newState = currentState

        when (data[0].toInt() and 0xFF) {
            0x01 -> {
                // Car type: data format: [01, mainSeries, series, type, batch, feature, reverse]
                if (message.len >= 6) {
                    val series = data[2].toInt() and 0xFF
                    val type = data[3].toInt() and 0xFF
                    model = Model.findById(series, type)
                    isModelDetected = true
                    newState = newState.copy(
                        model = model.displayName,
                        wheelType = WheelType.INMOTION_V2
                    )
                }
            }
            0x02 -> {
                // Serial number
                if (message.len >= 17) {
                    serialNumber = data.copyOfRange(1, 17).decodeToString()
                    newState = newState.copy(serialNumber = serialNumber)
                }
            }
            0x06 -> {
                // Versions
                if (message.len >= 24) {
                    protoVer = 0
                    val driverBoard3 = ByteUtils.shortFromBytesLE(data, 2)
                    val driverBoard2 = data[4].toInt() and 0xFF
                    val driverBoard1 = data[5].toInt() and 0xFF
                    val driverBoard = "$driverBoard1.$driverBoard2.$driverBoard3"

                    val mainBoard3 = ByteUtils.shortFromBytesLE(data, 11)
                    val mainBoard2 = data[13].toInt() and 0xFF
                    val mainBoard1 = data[14].toInt() and 0xFF
                    val mainBoard = "$mainBoard1.$mainBoard2.$mainBoard3"

                    val ble3 = ByteUtils.shortFromBytesLE(data, 20)
                    val ble2 = data[22].toInt() and 0xFF
                    val ble1 = data[23].toInt() and 0xFF
                    val ble = "$ble1.$ble2.$ble3"

                    version = "Main:$mainBoard Drv:$driverBoard BLE:$ble"
                    newState = newState.copy(version = version)

                    // Set protocol version for V11
                    if (model == Model.V11) {
                        protoVer = if (mainBoard1 < 2 && mainBoard2 < 4) 1 else 2
                    }
                }
            }
        }

        return ProcessResult(newState, false)
    }

    /**
     * Process settings data.
     */
    private fun processSettings(message: Message, currentState: WheelState): ProcessResult? {
        // Settings are parsed but don't update WheelState directly
        // They would typically update app configuration
        return when (model) {
            Model.V11 -> parseSettingsV11(message.data, currentState)
            Model.V11Y -> parseSettingsV11y(message.data, currentState)
            Model.V12HS, Model.V12HT, Model.V12PRO -> parseSettingsV12(message.data, currentState)
            Model.V13, Model.V13PRO -> parseSettingsV13(message.data, currentState)
            Model.V14g, Model.V14s -> parseSettingsV14(message.data, currentState)
            Model.V9 -> parseSettingsV9(message.data, currentState)
            Model.V12S -> parseSettingsV12S(message.data, currentState)
            else -> null
        }
    }

    /**
     * Process diagnostic data.
     */
    private fun processDiagnostic(message: Message, currentState: WheelState): ProcessResult? {
        // Check if all diagnostic bytes are 0 (OK)
        if (message.data.size > 7) {
            for (byte in message.data) {
                if (byte.toInt() != 0) {
                    // Has diagnostic errors, but we don't modify state for now
                    return null
                }
            }
        }
        return null
    }

    /**
     * Process battery real-time info.
     */
    private fun processBatteryRealTimeInfo(message: Message, currentState: WheelState): ProcessResult? {
        val data = message.data
        if (data.size < 20) return null

        // Parse battery data (not stored in WheelState currently)
        // val bat1Voltage = ByteUtils.shortFromBytesLE(data, 0)
        // val bat1Temp = data[4].toInt()
        // val bat2Voltage = ByteUtils.shortFromBytesLE(data, 8)
        // val bat2Temp = data[12].toInt()
        // val chargeVoltage = ByteUtils.shortFromBytesLE(data, 16)
        // val chargeCurrent = ByteUtils.shortFromBytesLE(data, 18)

        return null
    }

    /**
     * Process total statistics.
     */
    private fun processTotalStats(message: Message, currentState: WheelState): ProcessResult? {
        val data = message.data
        if (data.size < 20) return null

        val totalDistance = ByteUtils.intFromBytesLE(data, 0).toLong() * 10

        return ProcessResult(
            currentState.copy(totalDistance = totalDistance),
            false
        )
    }

    /**
     * Process real-time telemetry info.
     */
    private fun processRealTimeInfo(message: Message, currentState: WheelState): ProcessResult? {
        return when (model) {
            Model.V11 -> {
                if (protoVer < 2) {
                    parseRealTimeInfoV11(message.data, currentState)
                } else {
                    parseRealTimeInfoV11_1_4(message.data, currentState)
                }
            }
            Model.V11Y -> parseRealTimeInfoV11y(message.data, currentState)
            Model.V12HS, Model.V12HT, Model.V12PRO -> parseRealTimeInfoV12(message.data, currentState)
            Model.V13, Model.V13PRO -> parseRealTimeInfoV13(message.data, currentState)
            Model.V14g, Model.V14s -> parseRealTimeInfoV14(message.data, currentState)
            Model.V9 -> parseRealTimeInfoV9(message.data, currentState)
            Model.V12S -> parseRealTimeInfoV12S(message.data, currentState)
            else -> null
        }
    }

    // ==================== V11 Parsing ====================

    private fun parseRealTimeInfoV11(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 38) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val speed = ByteUtils.signedShortFromBytesLE(data, 4)
        val torque = ByteUtils.signedShortFromBytesLE(data, 6)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 8)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 10)
        val mileage = ByteUtils.shortFromBytesLE(data, 12) * 10
        val batLevel = data[16].toInt() and 0x7F
        val mosTemp = decodeTemperature(data[17])
        val boardTemp = decodeTemperature(data[20])
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 22)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 26)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 28)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 30)
        val cpuTemp = decodeTemperature(data[34])
        val imuTemp = decodeTemperature(data[35])
        val pwm = ByteUtils.shortFromBytesLE(data, 36)

        // State data
        val stateIndex = if (data.size < 49) 36 else 38
        val modeStr = buildModeString(data, stateIndex)
        val alert = getErrorString(data, stateIndex + 5)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage.toLong(),
                batteryLevel = batLevel,
                temperature = mosTemp * 100,
                temperature2 = boardTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    private fun parseRealTimeInfoV11_1_4(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 57) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val speed = ByteUtils.signedShortFromBytesLE(data, 4)
        val torque = ByteUtils.signedShortFromBytesLE(data, 6)
        val pwm = ByteUtils.signedShortFromBytesLE(data, 8)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 10)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 12)
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 16)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 20)
        val mileage = ByteUtils.shortFromBytesLE(data, 26) * 10
        val batLevel = ByteUtils.shortFromBytesLE(data, 28)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 34)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 36)
        val mosTemp = decodeTemperature(data[42])
        val boardTemp = decodeTemperature(data[45])
        val cpuTemp = decodeTemperature(data[46])
        val imuTemp = decodeTemperature(data[47])

        val modeStr = buildModeString(data, 56)
        val alert = getErrorString(data, 61)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage.toLong(),
                batteryLevel = (batLevel / 100.0).roundToInt(),
                temperature = mosTemp * 100,
                temperature2 = boardTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    // ==================== V12 Parsing ====================

    private fun parseRealTimeInfoV12(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 60) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val speed = ByteUtils.signedShortFromBytesLE(data, 4)
        val torque = ByteUtils.signedShortFromBytesLE(data, 6)
        val pwm = ByteUtils.signedShortFromBytesLE(data, 8)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 10)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 12)
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 16)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 20)
        val mileage = ByteUtils.shortFromBytesLE(data, 22) * 10
        val batLevel = ByteUtils.shortFromBytesLE(data, 24)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 30)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 32)
        val mosTemp = decodeTemperature(data[40])
        val motTemp = decodeTemperature(data[41])
        val cpuTemp = decodeTemperature(data[44])
        val imuTemp = decodeTemperature(data[45])

        val modeStr = buildModeString(data, 54)
        val alert = getErrorString(data, 59)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage.toLong(),
                batteryLevel = (batLevel / 100.0).roundToInt(),
                temperature = mosTemp * 100,
                temperature2 = motTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    // ==================== V13 Parsing ====================

    private fun parseRealTimeInfoV13(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 77) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 6)
        val speed = ByteUtils.signedShortFromBytesLE(data, 8)
        val mileage = ByteUtils.intFromBytesRevLE(data, 10)
        val pwm = ByteUtils.signedShortFromBytesLE(data, 14)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 16)
        val torque = ByteUtils.signedShortFromBytesLE(data, 18)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 22)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 24)
        val batLevel1 = ByteUtils.shortFromBytesLE(data, 34)
        val batLevel2 = ByteUtils.shortFromBytesLE(data, 36)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 40)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 50)
        val mosTemp = decodeTemperature(data[58])
        val motTemp = decodeTemperature(data[59])
        val cpuTemp = decodeTemperature(data[62])
        val imuTemp = decodeTemperature(data[63])

        val modeStr = buildModeString(data, 74)
        val alert = getErrorString(data, 76)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage,
                batteryLevel = ((batLevel1 + batLevel2) / 200.0).roundToInt(),
                temperature = mosTemp * 100,
                temperature2 = motTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    // ==================== V14 Parsing ====================

    private fun parseRealTimeInfoV14(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 78) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val speed = ByteUtils.signedShortFromBytesLE(data, 8)
        val torque = ByteUtils.signedShortFromBytesLE(data, 12)
        val pwm = ByteUtils.signedShortFromBytesLE(data, 14)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 16)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 18)
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 20)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 22)
        val mileage = ByteUtils.shortFromBytesLE(data, 28) * 10
        val batLevel1 = ByteUtils.shortFromBytesLE(data, 34)
        val batLevel2 = ByteUtils.shortFromBytesLE(data, 36)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 40)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 50)
        val mosTemp = decodeTemperature(data[58])
        val motTemp = decodeTemperature(data[59])
        val cpuTemp = decodeTemperature(data[62])
        val imuTemp = decodeTemperature(data[63])

        val modeStr = buildModeStringV14(data, 74)
        val alert = getErrorString(data, 77)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage.toLong(),
                batteryLevel = ((batLevel1 + batLevel2) / 200.0).roundToInt(),
                temperature = mosTemp * 100,
                temperature2 = motTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    // ==================== V11Y Parsing ====================

    private fun parseRealTimeInfoV11y(data: ByteArray, currentState: WheelState): ProcessResult? {
        if (data.size < 78) return null

        val voltage = ByteUtils.shortFromBytesLE(data, 0)
        val current = ByteUtils.signedShortFromBytesLE(data, 2)
        val speed = ByteUtils.signedShortFromBytesLE(data, 8)
        val torque = ByteUtils.signedShortFromBytesLE(data, 12)
        val pwm = ByteUtils.signedShortFromBytesLE(data, 14)
        val batPower = ByteUtils.signedShortFromBytesLE(data, 16)
        val motPower = ByteUtils.signedShortFromBytesLE(data, 18)
        val pitchAngle = ByteUtils.signedShortFromBytesLE(data, 20)
        val rollAngle = ByteUtils.signedShortFromBytesLE(data, 22)
        val mileage = ByteUtils.shortFromBytesLE(data, 28) * 10
        val batLevel1 = ByteUtils.shortFromBytesLE(data, 34)
        val batLevel2 = ByteUtils.shortFromBytesLE(data, 36)
        val dynamicSpeedLimit = ByteUtils.shortFromBytesLE(data, 40)
        val dynamicCurrentLimit = ByteUtils.shortFromBytesLE(data, 50)
        val mosTemp = decodeTemperature(data[58])
        val motTemp = decodeTemperature(data[59])
        val cpuTemp = decodeTemperature(data[62])
        val imuTemp = decodeTemperature(data[63])

        val modeStr = buildModeStringExtended(data, 74)
        val alert = getErrorString(data, 77)

        return ProcessResult(
            state = currentState.copy(
                voltage = voltage,
                current = current,
                speed = speed,
                torque = torque / 100.0,
                motorPower = motPower.toDouble(),
                power = batPower * 100,
                wheelDistance = mileage.toLong(),
                batteryLevel = ((batLevel1 + batLevel2) / 200.0).roundToInt(),
                temperature = mosTemp * 100,
                temperature2 = motTemp * 100,
                angle = pitchAngle / 100.0,
                roll = rollAngle / 100.0,
                speedLimit = dynamicSpeedLimit / 100.0,
                currentLimit = dynamicCurrentLimit / 100.0,
                cpuTemp = cpuTemp,
                imuTemp = imuTemp,
                output = pwm,
                modeStr = modeStr,
                alert = alert,
                model = model.displayName,
                wheelType = WheelType.INMOTION_V2
            ),
            hasNewData = true,
            news = alert.ifEmpty { null }
        )
    }

    // ==================== V9 Parsing ====================

    private fun parseRealTimeInfoV9(data: ByteArray, currentState: WheelState): ProcessResult? {
        // V9 uses same format as V11Y
        return parseRealTimeInfoV11y(data, currentState)
    }

    // ==================== V12S Parsing ====================

    private fun parseRealTimeInfoV12S(data: ByteArray, currentState: WheelState): ProcessResult? {
        // V12S uses same format as V11Y
        return parseRealTimeInfoV11y(data, currentState)
    }

    // ==================== Settings Parsing ====================

    private fun parseSettingsV11(data: ByteArray, currentState: WheelState): ProcessResult? {
        // Settings don't modify WheelState, they would update app config
        return null
    }

    private fun parseSettingsV11y(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    private fun parseSettingsV12(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    private fun parseSettingsV13(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    private fun parseSettingsV14(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    private fun parseSettingsV9(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    private fun parseSettingsV12S(data: ByteArray, currentState: WheelState): ProcessResult? {
        return null
    }

    // ==================== Helper Functions ====================

    /**
     * Decode temperature from byte (offset encoding: value + 80 - 256).
     */
    private fun decodeTemperature(byte: Byte): Int {
        return (byte.toInt() and 0xFF) + 80 - 256
    }

    /**
     * Build mode string from state data.
     */
    private fun buildModeString(data: ByteArray, index: Int): String {
        if (data.size <= index + 1) return ""

        val stateByte = data[index].toInt() and 0xFF
        val stateByte2 = data[index + 1].toInt() and 0xFF

        val motState = (stateByte shr 6) and 0x01
        val chrgState = (stateByte shr 7) and 0x01
        val liftedState = (stateByte2 shr 2) and 0x01

        return buildString {
            if (motState == 1) append("Active")
            if (chrgState == 1) append(" Charging")
            if (liftedState == 1) append(" Lifted")
        }.trim()
    }

    /**
     * Build mode string for V14 format.
     */
    private fun buildModeStringV14(data: ByteArray, index: Int): String {
        if (data.size <= index + 2) return ""

        val stateByte = data[index].toInt() and 0xFF
        val stateByte3 = data[index + 2].toInt() and 0xFF

        val motState = (stateByte shr 6) and 0x01
        val chrgState = (stateByte shr 7) and 0x01
        val liftedState = (stateByte3 shr 2) and 0x01

        return buildString {
            if (motState == 1) append("Active")
            if (chrgState == 1) append(" Charging")
            if (liftedState == 1) append(" Lifted")
        }.trim()
    }

    /**
     * Build mode string for extended format (V11Y, V9, V12S).
     */
    private fun buildModeStringExtended(data: ByteArray, index: Int): String {
        if (data.size <= index + 2) return ""

        val stateByte = data[index].toInt() and 0xFF
        val stateByte2 = data[index + 1].toInt() and 0xFF

        val motState = (stateByte shr 6) and 0x01
        val chrgState = (stateByte shr 7) and 0x01
        val liftedState = (stateByte2 shr 2) and 0x01

        return buildString {
            if (motState == 1) append("Active")
            if (chrgState == 1) append(" Charging")
            if (liftedState == 1) append(" Lifted")
        }.trim()
    }

    /**
     * Get error string from error bytes.
     */
    private fun getErrorString(data: ByteArray, index: Int): String {
        if (data.size < index + 7) return ""

        return buildString {
            // Byte 0 errors
            if ((data[index].toInt() and 0x01) == 1) append("err_iPhaseSensorState ")
            if ((data[index].toInt() shr 1 and 0x01) == 1) append("err_iBusSensorState ")
            if ((data[index].toInt() shr 2 and 0x01) == 1) append("err_motorHallState ")
            if ((data[index].toInt() shr 3 and 0x01) == 1) append("err_batteryState ")
            if ((data[index].toInt() shr 4 and 0x01) == 1) append("err_imuSensorState ")
            if ((data[index].toInt() shr 5 and 0x01) == 1) append("err_controllerCom1State ")
            if ((data[index].toInt() shr 6 and 0x01) == 1) append("err_controllerCom2State ")
            if ((data[index].toInt() shr 7 and 0x01) == 1) append("err_bleCom1State ")

            // Byte 1 errors
            if ((data[index + 1].toInt() and 0x01) == 1) append("err_bleCom2State ")
            if ((data[index + 1].toInt() shr 1 and 0x01) == 1) append("err_mosTempSensorState ")
            if ((data[index + 1].toInt() shr 2 and 0x01) == 1) append("err_motorTempSensorState ")
            if ((data[index + 1].toInt() shr 3 and 0x01) == 1) append("err_batteryTempSensorState ")
            if ((data[index + 1].toInt() shr 4 and 0x01) == 1) append("err_boardTempSensorState ")
            if ((data[index + 1].toInt() shr 5 and 0x01) == 1) append("err_fanState ")
            if ((data[index + 1].toInt() shr 6 and 0x01) == 1) append("err_rtcState ")
            if ((data[index + 1].toInt() shr 7 and 0x01) == 1) append("err_externalRomState ")

            // Byte 2 errors
            if ((data[index + 2].toInt() and 0x01) == 1) append("err_vBusSensorState ")
            if ((data[index + 2].toInt() shr 1 and 0x01) == 1) append("err_vBatterySensorState ")
            if ((data[index + 2].toInt() shr 2 and 0x01) == 1) append("err_canNotPowerOffState ")
            if ((data[index + 2].toInt() shr 3 and 0x01) == 1) append("err_notKnown1 ")

            // Byte 3 errors
            if ((data[index + 3].toInt() and 0x01) == 1) append("err_underVoltageState ")
            if ((data[index + 3].toInt() shr 1 and 0x01) == 1) append("err_overVoltageState ")
            val overBusCurrent = (data[index + 3].toInt() shr 2 and 0x03)
            if (overBusCurrent > 0) append("err_overBusCurrentState-$overBusCurrent ")
            val lowBattery = (data[index + 3].toInt() shr 4 and 0x03)
            if (lowBattery > 0) append("err_lowBatteryState-$lowBattery ")
            if ((data[index + 3].toInt() shr 6 and 0x01) == 1) append("err_mosTempState ")
            if ((data[index + 3].toInt() shr 7 and 0x01) == 1) append("err_motorTempState ")

            // Byte 4 errors
            if ((data[index + 4].toInt() and 0x01) == 1) append("err_batteryTempState ")
            if ((data[index + 4].toInt() shr 1 and 0x01) == 1) append("err_overBoardTempState ")
            if ((data[index + 4].toInt() shr 2 and 0x01) == 1) append("err_overSpeedState ")
            if ((data[index + 4].toInt() shr 3 and 0x01) == 1) append("err_outputSaturationState ")
            if ((data[index + 4].toInt() shr 4 and 0x01) == 1) append("err_motorSpinState ")
            if ((data[index + 4].toInt() shr 5 and 0x01) == 1) append("err_motorBlockState ")
            if ((data[index + 4].toInt() shr 6 and 0x01) == 1) append("err_postureState ")
            if ((data[index + 4].toInt() shr 7 and 0x01) == 1) append("err_riskBehaviourState ")

            // Byte 5 errors
            if ((data[index + 5].toInt() and 0x01) == 1) append("err_motorNoLoadState ")
            if ((data[index + 5].toInt() shr 1 and 0x01) == 1) append("err_noSelfTestState ")
            if ((data[index + 5].toInt() shr 2 and 0x01) == 1) append("err_compatibilityState ")
            if ((data[index + 5].toInt() shr 3 and 0x01) == 1) append("err_powerKeyLongPressState ")
            if ((data[index + 5].toInt() shr 4 and 0x01) == 1) append("err_forceDfuState ")
            if ((data[index + 5].toInt() shr 5 and 0x01) == 1) append("err_deviceLockState ")
            if ((data[index + 5].toInt() shr 6 and 0x01) == 1) append("err_cpuOverTempState ")
            if ((data[index + 5].toInt() shr 7 and 0x01) == 1) append("err_imuOverTempState ")

            // Byte 6 errors
            if ((data[index + 6].toInt() shr 1 and 0x01) == 1) append("err_hwCompatibilityState ")
            if ((data[index + 6].toInt() shr 2 and 0x01) == 1) append("err_fanLowSpeedState ")
            if ((data[index + 6].toInt() shr 3 and 0x01) == 1) append("err_notKnown2 ")
        }.trim()
    }

    override fun isReady(): Boolean = isModelDetected && version.isNotEmpty()

    override fun reset() {
        unpacker.reset()
        model = Model.UNKNOWN
        protoVer = 0
        serialNumber = ""
        version = ""
        isModelDetected = false
    }

    override fun getInitCommands(): List<WheelCommand> {
        return listOf(
            WheelCommand.SendBytes(buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x01))),
            WheelCommand.SendDelayed(buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x02)), 100),
            WheelCommand.SendDelayed(buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x06)), 200),
            WheelCommand.SendDelayed(buildMessage(Flag.DEFAULT, Command.SETTINGS, byteArrayOf(0x20)), 300),
            WheelCommand.SendDelayed(buildMessage(Flag.DEFAULT, Command.TOTAL_STATS, byteArrayOf()), 400)
        )
    }

    override fun getKeepAliveCommand(): WheelCommand {
        return WheelCommand.SendBytes(buildMessage(Flag.DEFAULT, Command.REAL_TIME_INFO, byteArrayOf()))
    }

    // ==================== Message Building ====================

    /**
     * Build a message to send to the wheel.
     */
    private fun buildMessage(flags: Int, command: Int, data: ByteArray): ByteArray {
        val buffer = mutableListOf<Byte>()

        // Add flags
        buffer.add(flags.toByte())
        // Add length (data length + 1 for command byte)
        buffer.add((data.size + 1).toByte())
        // Add command
        buffer.add(command.toByte())
        // Add data
        buffer.addAll(data.toList())

        // Calculate checksum
        var check = 0
        for (byte in buffer) {
            check = (check xor (byte.toInt() and 0xFF)) and 0xFF
        }

        // Build output with header and escape sequences
        val output = mutableListOf<Byte>()
        output.add(0xAA.toByte())
        output.add(0xAA.toByte())

        for (byte in buffer) {
            val b = byte.toInt() and 0xFF
            if (b == 0xAA || b == 0xA5) {
                output.add(0xA5.toByte())
            }
            output.add(byte)
        }

        output.add(check.toByte())

        return output.toByteArray()
    }

    companion object {
        /**
         * Create a message for requesting car type.
         */
        fun getCarTypeMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x01))
        }

        /**
         * Create a message for requesting serial number.
         */
        fun getSerialNumberMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x02))
        }

        /**
         * Create a message for requesting versions.
         */
        fun getVersionsMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.INITIAL, Command.MAIN_INFO, byteArrayOf(0x06))
        }

        /**
         * Create a message for requesting current settings.
         */
        fun getCurrentSettingsMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.DEFAULT, Command.SETTINGS, byteArrayOf(0x20))
        }

        /**
         * Create a message for requesting real-time data.
         */
        fun getRealTimeDataMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.DEFAULT, Command.REAL_TIME_INFO, byteArrayOf())
        }

        /**
         * Create a message for requesting total stats.
         */
        fun getStatisticsMessage(): ByteArray {
            return InmotionV2Decoder().buildMessage(Flag.DEFAULT, Command.TOTAL_STATS, byteArrayOf())
        }

        /**
         * Create a message to set light state.
         */
        fun setLightMessage(on: Boolean): ByteArray {
            val enable: Byte = if (on) 1 else 0
            return InmotionV2Decoder().buildMessage(Flag.DEFAULT, Command.CONTROL, byteArrayOf(0x50, enable))
        }

        /**
         * Create a message to set lock state.
         */
        fun setLockMessage(locked: Boolean): ByteArray {
            val enable: Byte = if (locked) 1 else 0
            return InmotionV2Decoder().buildMessage(Flag.DEFAULT, Command.CONTROL, byteArrayOf(0x31, enable))
        }

        /**
         * Create a message to play a beep.
         */
        fun playBeepMessage(number: Int = 0x18): ByteArray {
            return InmotionV2Decoder().buildMessage(
                Flag.DEFAULT,
                Command.CONTROL,
                byteArrayOf(0x51, (number and 0xFF).toByte(), 0x01)
            )
        }
    }
}
