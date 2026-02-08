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
    val wheelPassword: String = ""
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

    /**
     * Request to play a beep on the wheel.
     */
    data object Beep : WheelCommand()

    /**
     * Request to toggle the light.
     */
    data class SetLight(val enabled: Boolean) : WheelCommand()

    /**
     * Request to toggle the LED.
     */
    data class SetLed(val enabled: Boolean) : WheelCommand()

    /**
     * Set pedal mode/hardness.
     */
    data class SetPedalsMode(val mode: Int) : WheelCommand()

    /**
     * Set alarm mode.
     */
    data class SetAlarmMode(val mode: Int) : WheelCommand()

    /**
     * Calibrate the wheel.
     */
    data object Calibrate : WheelCommand()

    /**
     * Power off the wheel.
     */
    data object PowerOff : WheelCommand()

    /**
     * Lock/unlock the wheel.
     */
    data class SetLock(val locked: Boolean) : WheelCommand()
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
