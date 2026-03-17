package org.freewheel.core.charger

import kotlin.math.min

/**
 * HW Charger (Roger/Pidzoom) BLE protocol — frame building, parsing, and constants.
 *
 * Frame format: [size][command][payload...][checksum]
 * - size = total frame length - 1 (excludes itself)
 * - checksum = sum of bytes[1] through bytes[len-2], AND 0xFF
 * - Float values are 4-byte little-endian IEEE 754
 */
object HwChargerProtocol {

    // ── Command bytes ──────────────────────────────────────────────
    const val CMD_FIRMWARE: Byte = 0x01
    const val CMD_AUTH: Byte = 0x02
    const val CMD_DEVICE_ID: Byte = 0x04
    const val CMD_SETPOINTS: Byte = 0x05
    const val CMD_STATUS: Byte = 0x06
    const val CMD_SET_VOLTAGE: Byte = 0x07
    const val CMD_SET_CURRENT: Byte = 0x08
    const val CMD_POWER_ON_OFF: Byte = 0x0B
    const val CMD_START_STOP: Byte = 0x0C
    const val CMD_AUTO_STOP: Byte = 0x14
    const val CMD_END_CHARGE_CUR: Byte = 0x15
    const val CMD_TWO_STAGE: Byte = 0x20
    const val CMD_POWER_LIMIT: Byte = 0x27

    // Status frame offsets
    const val STATUS_FRAME_SIZE = 49
    const val STATUS_AC_VOLTAGE_OFFSET = 2
    const val STATUS_AC_CURRENT_OFFSET = 6
    const val STATUS_AC_FREQUENCY_OFFSET = 10
    const val STATUS_TEMP1_OFFSET = 14
    const val STATUS_TEMP2_OFFSET = 18
    const val STATUS_DC_VOLTAGE_OFFSET = 22
    const val STATUS_DC_CURRENT_OFFSET = 26
    const val STATUS_CURRENT_LIMIT_OFFSET = 30
    const val STATUS_EFFICIENCY_OFFSET = 34
    const val STATUS_OUTPUT_ENABLED_OFFSET = 38

    // Setpoints frame minimum size for voltage + current
    const val SETPOINTS_TARGET_VOLTAGE_OFFSET = 2
    const val SETPOINTS_TARGET_CURRENT_OFFSET = 6

    // BLE UUIDs
    const val SERVICE_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    const val CHARACTERISTIC_UUID = "0000ffe2-0000-1000-8000-00805f9b34fb"

    // ── Frame building ─────────────────────────────────────────────

    /**
     * Build a complete frame: [size][command][payload][checksum]
     * Size = command(1) + payload.size + checksum(1) = payload.size + 2
     */
    fun buildFrame(command: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val size = payload.size + 2 // command + payload + checksum
        val frame = ByteArray(size + 1) // +1 for the size byte itself
        frame[0] = size.toByte()
        frame[1] = command
        payload.copyInto(frame, 2)
        frame[frame.size - 1] = checksum(frame)
        return frame
    }

    /**
     * Build the MD5 auth frame (cmd 0x02).
     * MD5(password UTF-8) -> lowercase hex string (32 chars) -> null-terminated -> 36 bytes total.
     */
    fun buildAuthFrame(md5Hash: ByteArray): ByteArray {
        val hexChars = md5Hash.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        val payload = ByteArray(33) // 32 hex chars + null terminator
        for (i in hexChars.indices) {
            payload[i] = hexChars[i].code.toByte()
        }
        payload[32] = 0 // null terminator
        return buildFrame(CMD_AUTH, payload)
    }

    /**
     * Build a float command frame (for voltage, current, end-of-charge current).
     */
    fun buildFloatCommand(command: Byte, value: Float): ByteArray {
        return buildFrame(command, encodeFloat(value))
    }

    /**
     * Build the output toggle frame (cmd 0x0C).
     * Huawei inverted: 0 = enable, 1 = disable.
     */
    fun buildOutputToggle(enable: Boolean): ByteArray {
        val payload = byteArrayOf(if (enable) 0 else 1, 0, 0, 0)
        return buildFrame(CMD_START_STOP, payload)
    }

    /**
     * Build a power limit command (cmd 0x27, 4-byte LE signed int).
     */
    fun buildPowerLimitCommand(watts: Int): ByteArray {
        return buildFrame(CMD_POWER_LIMIT, encodeInt(watts))
    }

    // ── Checksum ───────────────────────────────────────────────────

    /**
     * Calculate checksum: sum of bytes[1] through bytes[len-2], AND 0xFF.
     */
    fun checksum(frame: ByteArray): Byte {
        var sum = 0
        for (i in 1 until frame.size - 1) {
            sum += frame[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }

    /**
     * Validate a frame's checksum.
     */
    fun isChecksumValid(frame: ByteArray): Boolean {
        if (frame.size < 3) return false
        return frame[frame.size - 1] == checksum(frame)
    }

    // ── Float encoding/decoding (Little Endian IEEE 754) ───────────

    /**
     * Encode a Float as 4-byte little-endian.
     */
    fun encodeFloat(value: Float): ByteArray {
        val bits = value.toRawBits()
        return byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte()
        )
    }

    /**
     * Decode a 4-byte little-endian float from byte array at offset.
     */
    fun decodeFloat(bytes: ByteArray, offset: Int): Float {
        if (bytes.size < offset + 4) return 0f
        val bits = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    /**
     * Encode an Int as 4-byte little-endian.
     */
    fun encodeInt(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    // ── MTU chunking ───────────────────────────────────────────────

    /**
     * Split a frame into MTU-sized chunks for BLE write.
     */
    fun chunkForMtu(frame: ByteArray, mtuSize: Int = 20): List<ByteArray> {
        if (frame.size <= mtuSize) return listOf(frame)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < frame.size) {
            val end = min(offset + mtuSize, frame.size)
            chunks.add(frame.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }
}
