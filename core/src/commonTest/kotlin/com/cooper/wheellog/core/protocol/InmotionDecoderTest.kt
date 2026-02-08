package com.cooper.wheellog.core.protocol

import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for InmotionDecoder (V1 protocol) and InmotionV2Decoder.
 */
class InmotionDecoderTest {

    private val config = DecoderConfig(
        useMph = false,
        useFahrenheit = false,
        useCustomPercents = false
    )

    // Helper to convert hex string to ByteArray
    private fun String.hexToByteArray(): ByteArray {
        val hex = this.replace(" ", "")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun `InmotionUnpacker handles header correctly`() {
        val unpacker = InmotionUnpacker()

        // Send AA AA header
        assertFalse(unpacker.addChar(0xAA))
        assertFalse(unpacker.addChar(0xAA))

        // Now in collecting state, but no complete frame
        val buffer = unpacker.getBuffer()
        assertEquals(2, buffer.size)
        assertEquals(0xAA.toByte(), buffer[0])
        assertEquals(0xAA.toByte(), buffer[1])
    }

    @Test
    fun `InmotionUnpacker resets correctly`() {
        val unpacker = InmotionUnpacker()

        // Send some data
        unpacker.addChar(0xAA)
        unpacker.addChar(0xAA)
        unpacker.addChar(0x01)

        // Reset
        unpacker.reset()
        val buffer = unpacker.getBuffer()
        assertEquals(0, buffer.size)
    }

    @Test
    fun `InmotionV2Unpacker handles header correctly`() {
        val unpacker = InmotionV2Unpacker()

        // Send AA AA header
        assertFalse(unpacker.addChar(0xAA))
        assertFalse(unpacker.addChar(0xAA))

        // Now should be looking for flags
        val buffer = unpacker.getBuffer()
        assertEquals(2, buffer.size)
    }

    @Test
    fun `InmotionV2Unpacker handles escape bytes`() {
        val unpacker = InmotionV2Unpacker()

        // Start frame
        unpacker.addChar(0xAA)
        unpacker.addChar(0xAA)

        // Send flags
        unpacker.addChar(0x11) // INITIAL flag

        // Send length (includes command)
        unpacker.addChar(0x02) // 2 bytes: command + 1 data byte

        // Send command
        unpacker.addChar(0x01)

        // Send data byte
        unpacker.addChar(0x01)

        // Send checksum - XOR of (0x11 ^ 0x02 ^ 0x01 ^ 0x01) = 0x13
        val complete = unpacker.addChar(0x13)

        assertTrue(complete)
        val buffer = unpacker.getBuffer()
        assertEquals(7, buffer.size)
    }

    @Test
    fun `InmotionDecoder initialization`() {
        val decoder = InmotionDecoder()
        assertEquals(WheelType.INMOTION, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `InmotionDecoder reset clears state`() {
        val decoder = InmotionDecoder()

        // Process some data (even if invalid, it sets internal state)
        decoder.decode(byteArrayOf(0xAA.toByte(), 0xAA.toByte()), WheelState(), config)

        // Reset
        decoder.reset()
        assertFalse(decoder.isReady())
    }

    @Test
    fun `InmotionDecoder getKeepAliveCommand returns valid command`() {
        val decoder = InmotionDecoder()
        val command = decoder.getKeepAliveCommand()

        assertNotNull(command)
        assertTrue(command is WheelCommand.SendBytes)
        val bytes = (command as WheelCommand.SendBytes).data

        // Should start with AA AA header
        assertEquals(0xAA.toByte(), bytes[0])
        assertEquals(0xAA.toByte(), bytes[1])
    }

    @Test
    fun `InmotionV2Decoder initialization`() {
        val decoder = InmotionV2Decoder()
        assertEquals(WheelType.INMOTION_V2, decoder.wheelType)
        assertFalse(decoder.isReady())
    }

    @Test
    fun `InmotionV2Decoder reset clears state`() {
        val decoder = InmotionV2Decoder()
        decoder.reset()
        assertFalse(decoder.isReady())
    }

    @Test
    fun `InmotionV2Decoder getInitCommands returns valid commands`() {
        val decoder = InmotionV2Decoder()
        val commands = decoder.getInitCommands()

        assertTrue(commands.isNotEmpty())
        // First command should be car type request
        val firstCmd = commands[0]
        assertTrue(firstCmd is WheelCommand.SendBytes)
    }

    @Test
    fun `InmotionV2Decoder getKeepAliveCommand returns valid command`() {
        val decoder = InmotionV2Decoder()
        val command = decoder.getKeepAliveCommand()

        assertTrue(command is WheelCommand.SendBytes)
        val bytes = (command as WheelCommand.SendBytes).data

        // Should start with AA AA header
        assertEquals(0xAA.toByte(), bytes[0])
        assertEquals(0xAA.toByte(), bytes[1])
    }

    @Test
    fun `InmotionDecoder CANMessage standardMessage builds correctly`() {
        val msg = InmotionDecoder.CANMessage.standardMessage()
        assertEquals(InmotionDecoder.IDValue.GetFastInfo.value, msg.id)
        assertEquals(8, msg.len)
        assertEquals(5, msg.ch)
    }

    @Test
    fun `InmotionDecoder CANMessage getSlowData builds correctly`() {
        val msg = InmotionDecoder.CANMessage.getSlowData()
        assertEquals(InmotionDecoder.IDValue.GetSlowInfo.value, msg.id)
        assertEquals(8, msg.len)
        assertEquals(1, msg.type) // RemoteFrame
    }

    @Test
    fun `InmotionDecoder CANMessage setLight builds correctly`() {
        val msgOn = InmotionDecoder.CANMessage.setLight(true)
        assertEquals(1.toByte(), msgOn.data[0])

        val msgOff = InmotionDecoder.CANMessage.setLight(false)
        assertEquals(0.toByte(), msgOff.data[0])
    }

    @Test
    fun `InmotionDecoder Model findById returns correct model`() {
        val v8 = InmotionDecoder.Model.findById("80")
        assertEquals(InmotionDecoder.Model.V8, v8)

        val v10f = InmotionDecoder.Model.findById("141")
        assertEquals(InmotionDecoder.Model.V10F, v10f)

        val unknown = InmotionDecoder.Model.findById("999")
        assertEquals(InmotionDecoder.Model.UNKNOWN, unknown)
    }

    @Test
    fun `InmotionDecoder Model belongsToInputType works correctly`() {
        val v5 = InmotionDecoder.Model.V5
        assertTrue(v5.belongsToInputType("5"))
        assertFalse(v5.belongsToInputType("8"))

        val v8 = InmotionDecoder.Model.V8
        assertTrue(v8.belongsToInputType("8"))
        assertFalse(v8.belongsToInputType("5"))
    }

    @Test
    fun `InmotionDecoder batteryFromVoltage V8 series`() {
        // V8 at full charge (~84V)
        val fullBatt = InmotionDecoder.batteryFromVoltage(8400, InmotionDecoder.Model.V8, true)
        assertEquals(100, fullBatt)

        // V8 at empty (~68V)
        val emptyBatt = InmotionDecoder.batteryFromVoltage(6800, InmotionDecoder.Model.V8, true)
        assertEquals(0, emptyBatt)

        // V8 at mid charge (~76V)
        val midBatt = InmotionDecoder.batteryFromVoltage(7600, InmotionDecoder.Model.V8, true)
        assertTrue(midBatt in 40..60, "Mid battery should be around 50%: $midBatt")
    }

    @Test
    fun `InmotionV2Decoder Model findById returns correct model`() {
        val v11 = InmotionV2Decoder.Model.findById(6, 1)
        assertEquals(InmotionV2Decoder.Model.V11, v11)

        val v12hs = InmotionV2Decoder.Model.findById(7, 1)
        assertEquals(InmotionV2Decoder.Model.V12HS, v12hs)

        val v13 = InmotionV2Decoder.Model.findById(8, 1)
        assertEquals(InmotionV2Decoder.Model.V13, v13)

        val unknown = InmotionV2Decoder.Model.findById(99, 99)
        assertEquals(InmotionV2Decoder.Model.UNKNOWN, unknown)
    }

    @Test
    fun `InmotionV2Decoder static message builders work`() {
        val carTypeMsg = InmotionV2Decoder.getCarTypeMessage()
        assertTrue(carTypeMsg.isNotEmpty())
        assertEquals(0xAA.toByte(), carTypeMsg[0])
        assertEquals(0xAA.toByte(), carTypeMsg[1])

        val serialMsg = InmotionV2Decoder.getSerialNumberMessage()
        assertTrue(serialMsg.isNotEmpty())

        val versionsMsg = InmotionV2Decoder.getVersionsMessage()
        assertTrue(versionsMsg.isNotEmpty())

        val settingsMsg = InmotionV2Decoder.getCurrentSettingsMessage()
        assertTrue(settingsMsg.isNotEmpty())

        val realTimeMsg = InmotionV2Decoder.getRealTimeDataMessage()
        assertTrue(realTimeMsg.isNotEmpty())

        val statsMsg = InmotionV2Decoder.getStatisticsMessage()
        assertTrue(statsMsg.isNotEmpty())

        val lightOnMsg = InmotionV2Decoder.setLightMessage(true)
        assertTrue(lightOnMsg.isNotEmpty())

        val lockMsg = InmotionV2Decoder.setLockMessage(true)
        assertTrue(lockMsg.isNotEmpty())

        val beepMsg = InmotionV2Decoder.playBeepMessage()
        assertTrue(beepMsg.isNotEmpty())
    }

    @Test
    fun `InmotionDecoder getModelString returns correct names`() {
        assertEquals("Inmotion V8", InmotionDecoder.getModelString(InmotionDecoder.Model.V8))
        assertEquals("Inmotion V10F", InmotionDecoder.getModelString(InmotionDecoder.Model.V10F))
        assertEquals("Solowheel Glide 3", InmotionDecoder.getModelString(InmotionDecoder.Model.Glide3))
        assertEquals("Unknown", InmotionDecoder.getModelString(InmotionDecoder.Model.UNKNOWN))
    }

    @Test
    fun `InmotionDecoder keepAliveIntervalMs is correct`() {
        val decoder = InmotionDecoder()
        assertEquals(250L, decoder.keepAliveIntervalMs)
    }

    @Test
    fun `InmotionV2Decoder keepAliveIntervalMs is correct`() {
        val decoder = InmotionV2Decoder()
        assertEquals(25L, decoder.keepAliveIntervalMs)
    }
}
