package org.freewheel.core.protocol

import kotlin.math.min

/**
 * Frame unpacker for Gotway/Begode wheels.
 *
 * Gotway uses a serial-over-BLE protocol with 24-byte frames.
 * Frame format:
 * - Bytes 0-1: Header (55 AA)
 * - Bytes 2-17: Data payload
 * - Byte 18: Frame type
 * - Byte 19: Footer byte (typically 18)
 * - Bytes 20-23: Footer (5A 5A 5A 5A)
 */
internal class GotwayUnpacker : Unpacker {

    private enum class State {
        UNKNOWN,
        COLLECTING,
        DONE
    }

    private var buffer = ByteArrayBuilder()
    private var state = State.UNKNOWN
    private var oldC = -1

    // Error counters (persist across reset(), cleared by resetStats())
    private var _errorResets = 0
    private var _bytesDiscarded = 0

    override val stats: UnpackerStats get() = UnpackerStats(_errorResets, _bytesDiscarded)

    override fun resetStats() {
        _errorResets = 0
        _bytesDiscarded = 0
    }

    /**
     * Reset the unpacker state.
     */
    override fun reset() {
        buffer.clear()
        state = State.UNKNOWN
        oldC = -1
    }

    /**
     * Get the complete frame buffer.
     */
    override fun getBuffer(): ByteArray = buffer.toByteArray()

    /**
     * Add a byte to the unpacker.
     * @return true if a complete valid frame is ready
     */
    override fun addChar(c: Int): Boolean {
        val byte = c and 0xFF

        when (state) {
            State.COLLECTING -> {
                buffer.write(byte)
                val size = buffer.size()

                // Check footer bytes (should be 5A 5A 5A 5A)
                if (size > 20 && size <= 24 && byte != 0x5A) {
                    // Invalid frame footer — partial frame discarded
                    _errorResets++
                    _bytesDiscarded += size
                    state = State.UNKNOWN
                    return false
                }

                if (size == 24) {
                    state = State.DONE
                    return true
                }

                // Handle garbage packet detection (55 AA 5A followed by 55 AA)
                if (size == 5) {
                    if (buffer[0] == 0x55.toByte() && buffer[1] == 0xAA.toByte() &&
                        buffer[2] == 0x5A.toByte() && buffer[3] == 0x55.toByte() &&
                        buffer[4] == 0xAA.toByte()) {
                        // Garbage packet, reassemble
                        buffer.clear()
                        buffer.write(0x55)
                        buffer.write(0xAA)
                    }
                }

                // Handle another garbage pattern (55 AA 5A 5A 55 AA)
                if (size == 6) {
                    if (buffer[0] == 0x55.toByte() && buffer[1] == 0xAA.toByte() &&
                        buffer[2] == 0x5A.toByte() && buffer[3] == 0x5A.toByte() &&
                        buffer[4] == 0x55.toByte() && buffer[5] == 0xAA.toByte()) {
                        // Garbage packet, reassemble
                        buffer.clear()
                        buffer.write(0x55)
                        buffer.write(0xAA)
                    }
                }
            }

            else -> {
                // Looking for frame header (55 AA)
                if (byte == 0xAA && oldC == 0x55) {
                    buffer.clear()
                    buffer.write(0x55)
                    buffer.write(0xAA)
                    state = State.COLLECTING
                }
                oldC = byte
            }
        }

        return false
    }
}

/**
 * Simple ByteArrayOutputStream replacement for KMP.
 *
 * Uses a pre-allocated [ByteArray] to avoid boxing each byte into
 * `java.lang.Byte` (which `MutableList<Byte>` does on JVM).
 * At 40 Hz × ~24 bytes/frame this eliminates ~960 boxing allocations/sec.
 *
 * Call [clear] to reset for reuse instead of allocating a new instance.
 */
class ByteArrayBuilder(capacity: Int = 512) {
    private var data = ByteArray(capacity)
    private var position = 0

    fun write(b: Int) {
        if (position == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[position++] = b.toByte()
    }

    fun size(): Int = position

    operator fun get(index: Int): Byte {
        if (index < 0 || index >= position) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $position")
        }
        return data[index]
    }

    fun clear() {
        position = 0
    }

    fun toByteArray(): ByteArray = data.copyOfRange(0, position)
}
