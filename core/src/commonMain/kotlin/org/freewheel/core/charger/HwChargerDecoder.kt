package org.freewheel.core.charger

import org.freewheel.core.charger.HwChargerProtocol.CMD_AUTH
import org.freewheel.core.charger.HwChargerProtocol.CMD_FIRMWARE
import org.freewheel.core.charger.HwChargerProtocol.CMD_SETPOINTS
import org.freewheel.core.charger.HwChargerProtocol.CMD_STATUS
import org.freewheel.core.charger.HwChargerProtocol.STATUS_AC_CURRENT_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_AC_FREQUENCY_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_AC_VOLTAGE_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_CURRENT_LIMIT_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_DC_CURRENT_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_DC_VOLTAGE_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_EFFICIENCY_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_FRAME_SIZE
import org.freewheel.core.charger.HwChargerProtocol.STATUS_OUTPUT_ENABLED_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_TEMP1_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.STATUS_TEMP2_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.SETPOINTS_TARGET_VOLTAGE_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.SETPOINTS_TARGET_CURRENT_OFFSET
import org.freewheel.core.charger.HwChargerProtocol.decodeFloat
import org.freewheel.core.charger.HwChargerProtocol.isChecksumValid
import org.freewheel.core.utils.currentTimeMillis

/**
 * Decodes incoming BLE indication data from an HW Charger.
 *
 * Accumulates raw BLE bytes into a buffer, extracts complete frames by size byte,
 * validates checksums, and dispatches to per-command parsers that update ChargerState.
 *
 * Frame accumulation logic from Roger app d.java:
 * 1. Append incoming bytes to buffer
 * 2. Read byte[0] as size; total frame = size + 1
 * 3. If buffer >= total, extract frame, keep remainder
 * 4. Repeat until buffer exhausted
 */
class HwChargerDecoder {

    private var buffer = ByteArray(0)

    /**
     * Feed raw BLE indication data. Returns updated state if any complete frames were decoded,
     * or null if no complete frames yet.
     */
    fun decode(data: ByteArray, currentState: ChargerState): ChargerState? {
        // Append incoming bytes
        buffer = buffer + data

        var state: ChargerState? = null
        var latestState = currentState

        // Extract complete frames
        while (true) {
            if (buffer.size < 2) break

            val size = buffer[0].toInt() and 0xFF
            if (size < 2) {
                // Invalid size — clear buffer
                buffer = ByteArray(0)
                break
            }

            val totalFrameLength = size + 1
            if (buffer.size < totalFrameLength) break // Wait for more data

            val frame = buffer.copyOfRange(0, totalFrameLength)
            buffer = buffer.copyOfRange(totalFrameLength, buffer.size)

            if (!isChecksumValid(frame)) continue

            val decoded = decodeFrame(frame, latestState)
            if (decoded != null) {
                latestState = decoded
                state = decoded
            }
        }

        return state
    }

    /**
     * Reset the accumulation buffer (e.g., on disconnect).
     */
    fun reset() {
        buffer = ByteArray(0)
    }

    // ── Frame dispatchers ──────────────────────────────────────────

    private fun decodeFrame(frame: ByteArray, currentState: ChargerState): ChargerState? {
        val command = frame[1]
        return when (command) {
            CMD_STATUS -> decodeStatus(frame, currentState)
            CMD_SETPOINTS -> decodeSetpoints(frame, currentState)
            CMD_FIRMWARE -> decodeFirmware(frame, currentState)
            CMD_AUTH -> decodeAuth(frame, currentState)
            else -> null // ACK or unknown — no state change
        }
    }

    /**
     * Status frame (cmd 0x06) — 49 bytes.
     * 9 LE floats + 1 status byte.
     */
    private fun decodeStatus(frame: ByteArray, currentState: ChargerState): ChargerState? {
        if (frame.size < STATUS_FRAME_SIZE) return null

        val outputEnabledByte = frame[STATUS_OUTPUT_ENABLED_OFFSET].toInt() and 0xFF
        // Huawei inverted: 0 = ON, non-zero = OFF
        val isOutputEnabled = outputEnabledByte == 0

        return currentState.copy(
            acVoltage = decodeFloat(frame, STATUS_AC_VOLTAGE_OFFSET),
            acCurrent = decodeFloat(frame, STATUS_AC_CURRENT_OFFSET),
            acFrequency = decodeFloat(frame, STATUS_AC_FREQUENCY_OFFSET),
            temperature1 = decodeFloat(frame, STATUS_TEMP1_OFFSET),
            temperature2 = decodeFloat(frame, STATUS_TEMP2_OFFSET),
            dcVoltage = decodeFloat(frame, STATUS_DC_VOLTAGE_OFFSET),
            dcCurrent = decodeFloat(frame, STATUS_DC_CURRENT_OFFSET),
            currentLimitingPoint = decodeFloat(frame, STATUS_CURRENT_LIMIT_OFFSET),
            efficiency = decodeFloat(frame, STATUS_EFFICIENCY_OFFSET),
            isOutputEnabled = isOutputEnabled,
            lastUpdateMs = currentTimeMillis()
        )
    }

    /**
     * Setpoints frame (cmd 0x05) — at least 10 bytes for voltage + current.
     */
    private fun decodeSetpoints(frame: ByteArray, currentState: ChargerState): ChargerState? {
        if (frame.size < 10) return null
        return currentState.copy(
            targetVoltage = decodeFloat(frame, SETPOINTS_TARGET_VOLTAGE_OFFSET),
            targetCurrent = decodeFloat(frame, SETPOINTS_TARGET_CURRENT_OFFSET),
            lastUpdateMs = currentTimeMillis()
        )
    }

    /**
     * Firmware frame (cmd 0x01) — UTF-8 string from bytes[2] to bytes[len-2].
     */
    private fun decodeFirmware(frame: ByteArray, currentState: ChargerState): ChargerState? {
        if (frame.size < 4) return null
        val stringBytes = frame.copyOfRange(2, frame.size - 1)
        val version = stringBytes.decodeToString().trimEnd('\u0000')
        return currentState.copy(
            firmwareVersion = version,
            lastUpdateMs = currentTimeMillis()
        )
    }

    /**
     * Auth response (cmd 0x02) — byte[2] == 1 means success.
     */
    private fun decodeAuth(frame: ByteArray, currentState: ChargerState): ChargerState? {
        if (frame.size < 4) return null
        val success = (frame[2].toInt() and 0xFF) == 1
        return currentState.copy(
            isAuthenticated = success,
            lastUpdateMs = currentTimeMillis()
        )
    }
}
