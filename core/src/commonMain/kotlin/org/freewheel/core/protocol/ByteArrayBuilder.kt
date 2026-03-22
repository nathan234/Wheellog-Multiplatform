package org.freewheel.core.protocol

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
