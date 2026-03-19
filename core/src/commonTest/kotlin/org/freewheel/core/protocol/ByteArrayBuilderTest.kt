package org.freewheel.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals

class ByteArrayBuilderTest {

    @Test
    fun emptyBuilderHasSizeZero() {
        val builder = ByteArrayBuilder()
        assertEquals(0, builder.size())
        assertContentEquals(byteArrayOf(), builder.toByteArray())
    }

    @Test
    fun writeAndRead() {
        val builder = ByteArrayBuilder()
        builder.write(0x55)
        builder.write(0xAA)
        builder.write(0x00)
        assertEquals(3, builder.size())
        assertEquals(0x55.toByte(), builder[0])
        assertEquals(0xAA.toByte(), builder[1])
        assertEquals(0x00.toByte(), builder[2])
        assertContentEquals(byteArrayOf(0x55, 0xAA.toByte(), 0x00), builder.toByteArray())
    }

    @Test
    fun clearResetsToEmpty() {
        val builder = ByteArrayBuilder()
        builder.write(0x01)
        builder.write(0x02)
        builder.write(0x03)
        assertEquals(3, builder.size())

        builder.clear()
        assertEquals(0, builder.size())
        assertContentEquals(byteArrayOf(), builder.toByteArray())

        // Can write again after clear
        builder.write(0xFF)
        assertEquals(1, builder.size())
        assertEquals(0xFF.toByte(), builder[0])
    }

    @Test
    fun toByteArrayReturnsCopy() {
        val builder = ByteArrayBuilder()
        builder.write(0x01)
        val arr1 = builder.toByteArray()
        builder.write(0x02)
        val arr2 = builder.toByteArray()

        // arr1 should not be affected by subsequent writes
        assertEquals(1, arr1.size)
        assertEquals(2, arr2.size)
    }

    @Test
    fun indexOutOfBoundsThrows() {
        val builder = ByteArrayBuilder()
        builder.write(0x01)
        assertFailsWith<IndexOutOfBoundsException> { builder[1] }
        assertFailsWith<IndexOutOfBoundsException> { builder[-1] }
    }

    @Test
    fun growsBeyondInitialCapacity() {
        val builder = ByteArrayBuilder(capacity = 4)
        for (i in 0 until 10) {
            builder.write(i)
        }
        assertEquals(10, builder.size())
        for (i in 0 until 10) {
            assertEquals(i.toByte(), builder[i])
        }
    }

    @Test
    fun clearAndReuseMultipleTimes() {
        val builder = ByteArrayBuilder()
        repeat(3) { round ->
            builder.clear()
            for (i in 0..round) {
                builder.write(round * 10 + i)
            }
            assertEquals(round + 1, builder.size())
        }
    }

    @Test
    fun writeHighByteValues() {
        val builder = ByteArrayBuilder()
        // write() takes an Int, stores as toByte()
        builder.write(0xFF)
        builder.write(0x80)
        builder.write(0x7F)
        assertEquals(0xFF.toByte(), builder[0])
        assertEquals(0x80.toByte(), builder[1])
        assertEquals(0x7F.toByte(), builder[2])
    }
}
