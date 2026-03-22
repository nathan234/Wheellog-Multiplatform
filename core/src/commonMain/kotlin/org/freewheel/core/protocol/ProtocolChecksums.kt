package org.freewheel.core.protocol

/**
 * Shared checksum functions used across multiple protocol decoders.
 */
internal object ProtocolChecksums {

    /**
     * CRC16 sum-based checksum used by Ninebot and Ninebot Z protocols.
     * Sums all bytes, then XORs with 0xFFFF.
     */
    fun sumCrc16(buffer: ByteArray): Int {
        var check = 0
        for (byte in buffer) {
            check += (byte.toInt() and 0xFF)
        }
        check = check xor 0xFFFF
        check = check and 0xFFFF
        return check
    }

    /**
     * CRC32 (polynomial 0xEDB88320) used by Veteran/Leaperkim protocol.
     */
    fun crc32(data: ByteArray, offset: Int, length: Int): Long {
        var crc = 0xFFFFFFFFL
        for (i in offset until offset + length) {
            val byte = data[i].toLong() and 0xFF
            crc = crc xor byte
            for (j in 0 until 8) {
                crc = if ((crc and 1L) == 1L) {
                    (crc ushr 1) xor 0xEDB88320L
                } else {
                    crc ushr 1
                }
            }
        }
        return crc xor 0xFFFFFFFFL
    }

    /**
     * XOR checksum used by InMotion V2 protocol.
     * XORs all bytes together.
     */
    fun xorChecksum(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        var check = 0
        for (i in offset until offset + length) {
            check = (check xor (buffer[i].toInt() and 0xFF)) and 0xFF
        }
        return check
    }
}
