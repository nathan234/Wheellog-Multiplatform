package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * Tests for NinebotDecoder and NinebotZDecoder.
 */
class NinebotDecoderTest {

    private val config = DecoderConfig(
        useMph = false,
        useFahrenheit = false,
        useCustomPercents = false
    )


    @Test
    fun `NinebotUnpacker handles header correctly`() {
        val unpacker = NinebotUnpacker()

        // Send 55 AA header
        assertFalse(unpacker.addChar(0x55))
        assertFalse(unpacker.addChar(0xAA))

        // Now in STARTED state
        val buffer = unpacker.getBuffer()
        assertEquals(2, buffer.size)
        assertEquals(0x55.toByte(), buffer[0])
        assertEquals(0xAA.toByte(), buffer[1])
    }

    @Test
    fun `NinebotUnpacker resets correctly`() {
        val unpacker = NinebotUnpacker()

        // Send some data
        unpacker.addChar(0x55)
        unpacker.addChar(0xAA)
        unpacker.addChar(0x05)

        // Reset
        unpacker.reset()
        val buffer = unpacker.getBuffer()
        assertEquals(0, buffer.size)
    }

    @Test
    fun `NinebotZUnpacker handles header correctly`() {
        val unpacker = NinebotZUnpacker()

        // Send 5A A5 header
        assertFalse(unpacker.addChar(0x5A))
        assertFalse(unpacker.addChar(0xA5))

        // Now in STARTED state
        val buffer = unpacker.getBuffer()
        assertEquals(2, buffer.size)
        assertEquals(0x5A.toByte(), buffer[0])
        assertEquals(0xA5.toByte(), buffer[1])
    }

    @Test
    fun `NinebotZUnpacker resets correctly`() {
        val unpacker = NinebotZUnpacker()

        // Send some data
        unpacker.addChar(0x5A)
        unpacker.addChar(0xA5)
        unpacker.addChar(0x05)

        // Reset
        unpacker.reset()
        val buffer = unpacker.getBuffer()
        assertEquals(0, buffer.size)
    }

    @Test
    fun `NinebotDecoder initialization`() {
        val decoder = NinebotDecoder()
        assertEquals(WheelType.NINEBOT, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotDecoder with S2 protocol`() {
        val decoder = NinebotDecoder(NinebotDecoder.ProtoVersion.S2)
        assertEquals(WheelType.NINEBOT, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotDecoder with Mini protocol`() {
        val decoder = NinebotDecoder(NinebotDecoder.ProtoVersion.MINI)
        assertEquals(WheelType.NINEBOT, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotDecoder reset clears state`() {
        val decoder = NinebotDecoder()
        decoder.reset()
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotDecoder getKeepAliveCommand returns valid command`() {
        val decoder = NinebotDecoder()
        val command = decoder.getKeepAliveCommand()

        assertNotNull(command)
        assertTrue(command is WheelCommand.SendBytes)
        val bytes = (command as WheelCommand.SendBytes).data

        // Should start with 55 AA header
        assertEquals(0x55.toByte(), bytes[0])
        assertEquals(0xAA.toByte(), bytes[1])
    }

    @Test
    fun `NinebotDecoder getInitCommands returns valid commands`() {
        val decoder = NinebotDecoder()
        val commands = decoder.getInitCommands()

        assertTrue(commands.isNotEmpty())
        val firstCmd = commands[0]
        assertTrue(firstCmd is WheelCommand.SendBytes)
    }

    @Test
    fun `NinebotZDecoder initialization`() {
        val decoder = NinebotZDecoder()
        assertEquals(WheelType.NINEBOT_Z, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotZDecoder reset clears state`() {
        val decoder = NinebotZDecoder()
        decoder.reset()
        assertFalse(decoder.isReady())

        // After reset, gamma should be all zeros
        val gamma = decoder.getGamma()
        assertEquals(16, gamma.size)
        assertTrue(gamma.all { it == 0.toByte() })
    }

    @Test
    fun `NinebotZDecoder setGamma and getGamma work correctly`() {
        val decoder = NinebotZDecoder()

        // Create a test gamma key
        val testGamma = ByteArray(16) { (it + 1).toByte() }
        decoder.setGamma(testGamma)

        val gamma = decoder.getGamma()
        assertEquals(16, gamma.size)
        for (i in 0 until 16) {
            assertEquals((i + 1).toByte(), gamma[i])
        }
    }

    @Test
    fun `NinebotZDecoder setGamma ignores invalid size`() {
        val decoder = NinebotZDecoder()

        // Try to set gamma with wrong size
        decoder.setGamma(ByteArray(10) { 0xFF.toByte() })

        // Should still be all zeros (unchanged)
        val gamma = decoder.getGamma()
        assertTrue(gamma.all { it == 0.toByte() })
    }

    @Test
    fun `NinebotZDecoder getKeepAliveCommand returns valid command`() {
        val decoder = NinebotZDecoder()
        val command = decoder.getKeepAliveCommand()

        assertNotNull(command)
        assertTrue(command is WheelCommand.SendBytes)
        val bytes = (command as WheelCommand.SendBytes).data

        // Should start with 5A A5 header
        assertEquals(0x5A.toByte(), bytes[0])
        assertEquals(0xA5.toByte(), bytes[1])
    }

    @Test
    fun `NinebotZDecoder getInitCommands returns valid commands`() {
        val decoder = NinebotZDecoder()
        val commands = decoder.getInitCommands()

        assertTrue(commands.isNotEmpty())
        val firstCmd = commands[0]
        assertTrue(firstCmd is WheelCommand.SendBytes)
    }

    @Test
    fun `NinebotZDecoder getBmsReadingMode`() {
        val decoder = NinebotZDecoder()

        // Initially not in BMS reading mode
        assertFalse(decoder.isReady())

        // Enable BMS reading mode
        decoder.setBmsReadingMode(true)

        // Still not ready until we receive data
        assertFalse(decoder.isReady())
    }

    @Test
    fun `NinebotZDecoder getCellsForWheel returns 14`() {
        val decoder = NinebotZDecoder()
        assertEquals(14, decoder.getCellsForWheel())
    }

    @Test
    fun `CANMessage crypto XOR works correctly`() {
        // Test data
        val input = byteArrayOf(0x05, 0x01, 0x02, 0x03, 0x04, 0x05)
        val gamma = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte(),
            0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x00)

        val encrypted = CANMessage.crypto(input, gamma)

        // First byte should be unchanged
        assertEquals(input[0], encrypted[0])

        // Subsequent bytes should be XORed with gamma
        assertEquals((0x01 xor 0x11).toByte(), encrypted[1])
        assertEquals((0x02 xor 0x22).toByte(), encrypted[2])
        assertEquals((0x03 xor 0x33).toByte(), encrypted[3])
        assertEquals((0x04 xor 0x44).toByte(), encrypted[4])
        assertEquals((0x05 xor 0x55).toByte(), encrypted[5])
    }

    @Test
    fun `CANMessage computeCheck calculates CRC correctly`() {
        // Simple test case
        val data = byteArrayOf(0x05, 0x3E, 0x14, 0x01, 0x10, 0x0E)
        val crc = CANMessage.computeCheck(data)

        // CRC should be sum XOR 0xFFFF
        val expectedSum = 0x05 + 0x3E + 0x14 + 0x01 + 0x10 + 0x0E
        val expectedCrc = (expectedSum xor 0xFFFF) and 0xFFFF
        assertEquals(expectedCrc, crc)
    }

    @Test
    fun `CANMessage factory methods return valid data`() {
        val gamma = ByteArray(16) { 0 }

        val bleVersion = CANMessage.getBleVersion(gamma)
        assertTrue(bleVersion.isNotEmpty())
        assertEquals(0x5A.toByte(), bleVersion[0])
        assertEquals(0xA5.toByte(), bleVersion[1])

        val serialNumber = CANMessage.getSerialNumber(gamma)
        assertTrue(serialNumber.isNotEmpty())

        val version = CANMessage.getVersion(gamma)
        assertTrue(version.isNotEmpty())

        val liveData = CANMessage.getLiveData(gamma)
        assertTrue(liveData.isNotEmpty())

        val params1 = CANMessage.getParams1(gamma)
        assertTrue(params1.isNotEmpty())

        val params2 = CANMessage.getParams2(gamma)
        assertTrue(params2.isNotEmpty())

        val params3 = CANMessage.getParams3(gamma)
        assertTrue(params3.isNotEmpty())
    }

    @Test
    fun `CANMessage BMS factory methods return valid data`() {
        val gamma = ByteArray(16) { 0 }

        val bms1Sn = CANMessage.getBms1Sn(gamma)
        assertTrue(bms1Sn.isNotEmpty())

        val bms1Life = CANMessage.getBms1Life(gamma)
        assertTrue(bms1Life.isNotEmpty())

        val bms1Cells = CANMessage.getBms1Cells(gamma)
        assertTrue(bms1Cells.isNotEmpty())

        val bms2Sn = CANMessage.getBms2Sn(gamma)
        assertTrue(bms2Sn.isNotEmpty())

        val bms2Life = CANMessage.getBms2Life(gamma)
        assertTrue(bms2Life.isNotEmpty())

        val bms2Cells = CANMessage.getBms2Cells(gamma)
        assertTrue(bms2Cells.isNotEmpty())
    }

    @Test
    fun `NinebotDecoder keepAliveIntervalMs is correct`() {
        val decoder = NinebotDecoder()
        assertEquals(125L, decoder.keepAliveIntervalMs)
    }

    @Test
    fun `NinebotZDecoder keepAliveIntervalMs is correct`() {
        val decoder = NinebotZDecoder()
        assertEquals(25L, decoder.keepAliveIntervalMs)
    }

    @Test
    fun `CANMessage Addr constants are correct`() {
        assertEquals(0x11, CANMessage.Addr.BMS1)
        assertEquals(0x12, CANMessage.Addr.BMS2)
        assertEquals(0x14, CANMessage.Addr.CONTROLLER)
        assertEquals(0x16, CANMessage.Addr.KEY_GENERATOR)
        assertEquals(0x3E, CANMessage.Addr.APP)
    }

    @Test
    fun `CANMessage Comm constants are correct`() {
        assertEquals(0x01, CANMessage.Comm.READ)
        assertEquals(0x03, CANMessage.Comm.WRITE)
        assertEquals(0x04, CANMessage.Comm.GET)
        assertEquals(0x5B, CANMessage.Comm.GET_KEY)
    }

    @Test
    fun `CANMessage Param constants are correct`() {
        assertEquals(0x00, CANMessage.Param.GET_KEY)
        assertEquals(0x10, CANMessage.Param.SERIAL_NUMBER)
        assertEquals(0x1A, CANMessage.Param.FIRMWARE)
        assertEquals(0x22, CANMessage.Param.BATTERY_LEVEL)
        assertEquals(0xB0, CANMessage.Param.LIVE_DATA)
    }

    @Test
    fun `NinebotZConnectionState values are sequential`() {
        assertEquals(0, NinebotZConnectionState.INIT.value)
        assertEquals(1, NinebotZConnectionState.WAIT_KEY.value)
        assertEquals(2, NinebotZConnectionState.SERIAL_NUMBER.value)
        assertEquals(3, NinebotZConnectionState.VERSION.value)
        assertEquals(13, NinebotZConnectionState.READY.value)
    }

    @Test
    fun `NinebotZConnectionState fromValue works correctly`() {
        assertEquals(NinebotZConnectionState.INIT, NinebotZConnectionState.fromValue(0))
        assertEquals(NinebotZConnectionState.READY, NinebotZConnectionState.fromValue(13))
        assertEquals(NinebotZConnectionState.INIT, NinebotZConnectionState.fromValue(999))
    }

    @Test
    fun `NinebotDecoder CELLS_FOR_WHEEL constant`() {
        assertEquals(15, NinebotDecoder.CELLS_FOR_WHEEL)
    }
}
