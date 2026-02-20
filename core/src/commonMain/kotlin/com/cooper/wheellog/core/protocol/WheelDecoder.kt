package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType

/**
 * Interface for wheel protocol decoders.
 * Each EUC manufacturer has its own BLE protocol, and this interface
 * abstracts the decoding logic to enable cross-platform sharing.
 */
interface WheelDecoder {
    /**
     * The wheel type this decoder handles.
     */
    val wheelType: WheelType

    /**
     * Decode incoming BLE data and produce updated state.
     *
     * @param data Raw bytes received from the wheel
     * @param currentState The current wheel state
     * @param config Decoder configuration options
     * @return Decoded result containing new state and any commands to send, or null if data was incomplete
     */
    fun decode(data: ByteArray, currentState: WheelState, config: DecoderConfig): DecodedData?

    /**
     * Check if the decoder has received enough data to be considered ready.
     * Some wheels require multiple data frames before all information is available.
     */
    fun isReady(): Boolean

    /**
     * Reset the decoder's internal state.
     * Called when disconnecting or switching wheels.
     */
    fun reset()

    /**
     * Get initialization commands to send after connecting.
     * Some wheels require specific commands to start sending telemetry.
     */
    fun getInitCommands(): List<WheelCommand> = emptyList()

    /**
     * Build protocol-specific byte commands for a high-level command.
     * Returns a list of SendBytes/SendDelayed commands, or empty list if unsupported.
     */
    fun buildCommand(command: WheelCommand): List<WheelCommand> = emptyList()

    /**
     * Get keep-alive commands that should be sent periodically.
     * Returns null if no keep-alive is needed.
     */
    fun getKeepAliveCommand(): WheelCommand? = null

    /**
     * Interval in milliseconds for keep-alive commands.
     */
    val keepAliveIntervalMs: Long get() = 0L
}

/**
 * Result from decoding wheel data.
 */
data class DecodedData(
    /**
     * Updated wheel state with new values.
     */
    val newState: WheelState,

    /**
     * Commands to send back to the wheel (e.g., acknowledgments, requests).
     */
    val commands: List<WheelCommand> = emptyList(),

    /**
     * Flag indicating if this decode produced new telemetry data.
     */
    val hasNewData: Boolean = true,

    /**
     * Optional news/message from the wheel.
     */
    val news: String? = null
)

/**
 * Configuration options for the decoder.
 */
data class DecoderConfig(
    /**
     * Use MPH instead of KM/H.
     */
    val useMph: Boolean = false,

    /**
     * Use Fahrenheit instead of Celsius.
     */
    val useFahrenheit: Boolean = false,

    /**
     * Use custom battery percentage calculation.
     */
    val useCustomPercents: Boolean = false,

    /**
     * Cell voltage considered empty (for custom percent calculation).
     */
    val cellVoltageTiltback: Int = 330,

    /**
     * Expected rotation speed at a given voltage (for PWM calculation).
     */
    val rotationSpeed: Int = 500,

    /**
     * Reference voltage for rotation speed (for PWM calculation).
     */
    val rotationVoltage: Int = 840,

    /**
     * Power factor for PWM calculation.
     */
    val powerFactor: Int = 100,

    /**
     * Battery capacity in Wh.
     */
    val batteryCapacity: Int = 0,

    /**
     * Password for InMotion wheels.
     */
    val wheelPassword: String = "",

    /**
     * Gotway speed/current polarity.
     * 0 = absolute value (always positive), default for most wheels.
     * 1 = keep original sign.
     * -1 = invert sign.
     */
    val gotwayNegative: Int = 0,

    /**
     * Apply 0.875 ratio to Gotway speed/distance values.
     * Some Gotway boards report inflated values that need scaling.
     */
    val useRatio: Boolean = false,

    /**
     * Gotway/Begode battery voltage configuration.
     * 0 = 67.2V (16S), 1 = 84V (20S), 2 = 100.8V (24S),
     * 3 = 126V (28S), 4 = 134.4V (32S), 5 = 168V (40S), 6 = 151V (36S).
     */
    val gotwayVoltage: Int = 0
)

/**
 * Commands that can be sent to the wheel.
 */
sealed class WheelCommand {
    /**
     * Send raw bytes to the wheel immediately.
     */
    data class SendBytes(val data: ByteArray) : WheelCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SendBytes) return false
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Send bytes after a delay.
     */
    data class SendDelayed(val data: ByteArray, val delayMs: Long) : WheelCommand() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SendDelayed) return false
            return data.contentEquals(other.data) && delayMs == other.delayMs
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + delayMs.hashCode()
            return result
        }
    }

    // --- Basic controls ---

    data object Beep : WheelCommand()
    data class SetLight(val enabled: Boolean) : WheelCommand()
    data class SetLightMode(val mode: Int) : WheelCommand()
    data class SetLed(val enabled: Boolean) : WheelCommand()
    data class SetLedMode(val mode: Int) : WheelCommand()
    data class SetStrobeMode(val mode: Int) : WheelCommand()
    data class SetPedalsMode(val mode: Int) : WheelCommand()
    data class SetAlarmMode(val mode: Int) : WheelCommand()
    data object Calibrate : WheelCommand()
    data object PowerOff : WheelCommand()
    data class SetLock(val locked: Boolean) : WheelCommand()
    data object ResetTrip : WheelCommand()

    // --- Speed & alarm settings ---

    data class SetMaxSpeed(val speed: Int) : WheelCommand()
    data class SetAlarmSpeed(val speed: Int, val num: Int) : WheelCommand()
    data class SetAlarmEnabled(val enabled: Boolean, val num: Int) : WheelCommand()
    data class SetLimitedMode(val enabled: Boolean) : WheelCommand()
    data class SetLimitedSpeed(val speed: Int) : WheelCommand()

    // --- Lighting ---

    data class SetTailLight(val enabled: Boolean) : WheelCommand()
    data class SetDrl(val enabled: Boolean) : WheelCommand()
    data class SetLedColor(val value: Int, val ledNum: Int) : WheelCommand()
    data class SetLightBrightness(val value: Int) : WheelCommand()

    // --- Ride modes & behavior ---

    data class SetHandleButton(val enabled: Boolean) : WheelCommand()
    data class SetBrakeAssist(val enabled: Boolean) : WheelCommand()
    data class SetTransportMode(val enabled: Boolean) : WheelCommand()
    data class SetRideMode(val enabled: Boolean) : WheelCommand()
    data class SetGoHomeMode(val enabled: Boolean) : WheelCommand()
    data class SetFancierMode(val enabled: Boolean) : WheelCommand()
    data class SetRollAngleMode(val mode: Int) : WheelCommand()

    // --- Audio ---

    data class SetMute(val enabled: Boolean) : WheelCommand()
    data class SetSpeakerVolume(val volume: Int) : WheelCommand()
    data class SetBeeperVolume(val volume: Int) : WheelCommand()

    // --- Thermal ---

    data class SetFanQuiet(val enabled: Boolean) : WheelCommand()
    data class SetFan(val enabled: Boolean) : WheelCommand()

    // --- Pedal tuning ---

    data class SetPedalTilt(val angle: Int) : WheelCommand()
    data class SetPedalSensitivity(val sensitivity: Int) : WheelCommand()

    // --- Units ---

    data class SetMilesMode(val enabled: Boolean) : WheelCommand()

    // --- Cutout angle (Begode) ---

    data class SetCutoutAngle(val angle: Int) : WheelCommand()

    // --- BMS requests (Kingsong) ---

    /**
     * Request BMS data. bmsNum: 1 or 2, dataType: 0=serial, 1=moreData, 2=firmware
     */
    data class RequestBmsData(val bmsNum: Int, val dataType: Int) : WheelCommand()

    // --- Kingsong alarm+speed combo ---

    /**
     * Set all three alarm speeds and max speed in one command (Kingsong-specific).
     */
    data class SetKingsongAlarms(
        val alarm1Speed: Int,
        val alarm2Speed: Int,
        val alarm3Speed: Int,
        val maxSpeed: Int
    ) : WheelCommand()

    /**
     * Request alarm settings and max speed from the wheel.
     */
    data object RequestAlarmSettings : WheelCommand()
}

/**
 * Factory for creating wheel decoders.
 */
interface WheelDecoderFactory {
    /**
     * Create a decoder for the specified wheel type.
     */
    fun createDecoder(wheelType: WheelType): WheelDecoder?

    /**
     * Get all supported wheel types.
     */
    fun supportedTypes(): List<WheelType>
}
