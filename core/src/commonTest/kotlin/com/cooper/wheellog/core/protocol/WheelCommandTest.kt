package com.cooper.wheellog.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for buildCommand() across all decoders that support horn and light commands.
 */
class WheelCommandTest {

    // ==================== Kingsong ====================

    @Test
    fun `KingsongDecoder beep returns frame with type 0x88`() {
        val decoder = KingsongDecoder()
        val commands = decoder.buildCommand(WheelCommand.Beep)
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals(20, cmd.data.size, "Kingsong frame must be 20 bytes")
        assertEquals(0xAA.toByte(), cmd.data[0], "Header byte 0")
        assertEquals(0x55.toByte(), cmd.data[1], "Header byte 1")
        assertEquals(0x88.toByte(), cmd.data[16], "Frame type should be 0x88")
        assertEquals(0x14.toByte(), cmd.data[17])
        assertEquals(0x5A.toByte(), cmd.data[18])
        assertEquals(0x5A.toByte(), cmd.data[19])
    }

    @Test
    fun `KingsongDecoder SetLight true returns frame with type 0x73`() {
        val decoder = KingsongDecoder()
        val commands = decoder.buildCommand(WheelCommand.SetLight(true))
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals(20, cmd.data.size)
        assertEquals(0x73.toByte(), cmd.data[16], "Frame type should be 0x73")
        assertEquals(0x12.toByte(), cmd.data[2], "Light on mode byte")
        assertEquals(0x01.toByte(), cmd.data[3], "Enable byte")
    }

    @Test
    fun `KingsongDecoder SetLight false returns frame with type 0x73`() {
        val decoder = KingsongDecoder()
        val commands = decoder.buildCommand(WheelCommand.SetLight(false))
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals(20, cmd.data.size)
        assertEquals(0x73.toByte(), cmd.data[16], "Frame type should be 0x73")
        assertEquals(0x13.toByte(), cmd.data[2], "Light off mode byte")
    }

    @Test
    fun `KingsongDecoder unsupported command returns empty list`() {
        val decoder = KingsongDecoder()
        assertTrue(decoder.buildCommand(WheelCommand.PowerOff).isEmpty())
    }

    // ==================== Veteran ====================

    @Test
    fun `VeteranDecoder beep returns SendBytes with b`() {
        val decoder = VeteranDecoder()
        val commands = decoder.buildCommand(WheelCommand.Beep)
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals("b", cmd.data.decodeToString())
    }

    @Test
    fun `VeteranDecoder SetLight true returns SetLightON`() {
        val decoder = VeteranDecoder()
        val commands = decoder.buildCommand(WheelCommand.SetLight(true))
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals("SetLightON", cmd.data.decodeToString())
    }

    @Test
    fun `VeteranDecoder SetLight false returns SetLightOFF`() {
        val decoder = VeteranDecoder()
        val commands = decoder.buildCommand(WheelCommand.SetLight(false))
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertEquals("SetLightOFF", cmd.data.decodeToString())
    }

    @Test
    fun `VeteranDecoder unsupported command returns empty list`() {
        val decoder = VeteranDecoder()
        assertTrue(decoder.buildCommand(WheelCommand.Calibrate).isEmpty())
    }

    // ==================== Inmotion V1 ====================

    @Test
    fun `InmotionDecoder beep returns non-empty result`() {
        val decoder = InmotionDecoder()
        val commands = decoder.buildCommand(WheelCommand.Beep)
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertTrue(cmd.data.isNotEmpty(), "Beep command should produce bytes")
    }

    @Test
    fun `InmotionDecoder SetLight returns non-empty result`() {
        val decoder = InmotionDecoder()
        val commandsOn = decoder.buildCommand(WheelCommand.SetLight(true))
        assertEquals(1, commandsOn.size)
        assertTrue((commandsOn[0] as WheelCommand.SendBytes).data.isNotEmpty())

        val commandsOff = decoder.buildCommand(WheelCommand.SetLight(false))
        assertEquals(1, commandsOff.size)
        assertTrue((commandsOff[0] as WheelCommand.SendBytes).data.isNotEmpty())
    }

    @Test
    fun `InmotionDecoder unsupported command returns empty list`() {
        val decoder = InmotionDecoder()
        assertTrue(decoder.buildCommand(WheelCommand.PowerOff).isEmpty())
    }

    // ==================== Inmotion V2 ====================

    @Test
    fun `InmotionV2Decoder beep returns non-empty result`() {
        val decoder = InmotionV2Decoder()
        val commands = decoder.buildCommand(WheelCommand.Beep)
        assertEquals(1, commands.size)
        val cmd = commands[0] as WheelCommand.SendBytes
        assertTrue(cmd.data.isNotEmpty(), "Beep command should produce bytes")
    }

    @Test
    fun `InmotionV2Decoder SetLight returns non-empty result`() {
        val decoder = InmotionV2Decoder()
        val commandsOn = decoder.buildCommand(WheelCommand.SetLight(true))
        assertEquals(1, commandsOn.size)
        assertTrue((commandsOn[0] as WheelCommand.SendBytes).data.isNotEmpty())

        val commandsOff = decoder.buildCommand(WheelCommand.SetLight(false))
        assertEquals(1, commandsOff.size)
        assertTrue((commandsOff[0] as WheelCommand.SendBytes).data.isNotEmpty())
    }

    @Test
    fun `InmotionV2Decoder unsupported command returns empty list`() {
        val decoder = InmotionV2Decoder()
        assertTrue(decoder.buildCommand(WheelCommand.Calibrate).isEmpty())
    }

    // ==================== Ninebot ====================

    @Test
    fun `NinebotDecoder returns empty list for all commands`() {
        val decoder = NinebotDecoder()
        assertTrue(decoder.buildCommand(WheelCommand.Beep).isEmpty())
        assertTrue(decoder.buildCommand(WheelCommand.SetLight(true)).isEmpty())
        assertTrue(decoder.buildCommand(WheelCommand.SetLight(false)).isEmpty())
    }

    // ==================== Ninebot Z ====================

    @Test
    fun `NinebotZDecoder returns empty list for all commands`() {
        val decoder = NinebotZDecoder()
        assertTrue(decoder.buildCommand(WheelCommand.Beep).isEmpty())
        assertTrue(decoder.buildCommand(WheelCommand.SetLight(true)).isEmpty())
        assertTrue(decoder.buildCommand(WheelCommand.SetLight(false)).isEmpty())
    }
}
