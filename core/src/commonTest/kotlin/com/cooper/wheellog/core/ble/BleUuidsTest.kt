package com.cooper.wheellog.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for BLE UUID constants and helper functions.
 */
class BleUuidsTest {

    @Test
    fun `Kingsong UUIDs are correct`() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleUuids.Kingsong.SERVICE)
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", BleUuids.Kingsong.READ_CHARACTERISTIC)
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", BleUuids.Kingsong.WRITE_CHARACTERISTIC)
    }

    @Test
    fun `Gotway UUIDs are correct`() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleUuids.Gotway.SERVICE)
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", BleUuids.Gotway.READ_CHARACTERISTIC)
    }

    @Test
    fun `Inmotion V1 UUIDs are correct`() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleUuids.Inmotion.READ_SERVICE)
        assertEquals("0000ffe4-0000-1000-8000-00805f9b34fb", BleUuids.Inmotion.READ_CHARACTERISTIC)
        assertEquals("0000ffe5-0000-1000-8000-00805f9b34fb", BleUuids.Inmotion.WRITE_SERVICE)
        assertEquals("0000ffe9-0000-1000-8000-00805f9b34fb", BleUuids.Inmotion.WRITE_CHARACTERISTIC)
    }

    @Test
    fun `Inmotion V2 UUIDs use Nordic UART`() {
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.InmotionV2.SERVICE)
        assertEquals("6e400002-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.InmotionV2.WRITE_CHARACTERISTIC)
        assertEquals("6e400003-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.InmotionV2.READ_CHARACTERISTIC)
    }

    @Test
    fun `Ninebot Z UUIDs use Nordic UART`() {
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.NinebotZ.SERVICE)
        assertEquals("6e400002-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.NinebotZ.WRITE_CHARACTERISTIC)
        assertEquals("6e400003-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.NinebotZ.READ_CHARACTERISTIC)
    }

    @Test
    fun `Inmotion V2 and Ninebot Z share same Nordic UART service`() {
        assertEquals(BleUuids.InmotionV2.SERVICE, BleUuids.NinebotZ.SERVICE)
        assertEquals(BleUuids.InmotionV2.READ_CHARACTERISTIC, BleUuids.NinebotZ.READ_CHARACTERISTIC)
        assertEquals(BleUuids.InmotionV2.WRITE_CHARACTERISTIC, BleUuids.NinebotZ.WRITE_CHARACTERISTIC)
    }

    @Test
    fun `Ninebot UUIDs are correct`() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleUuids.Ninebot.SERVICE)
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", BleUuids.Ninebot.READ_CHARACTERISTIC)
    }

    @Test
    fun `normalize converts to lowercase`() {
        assertEquals("0000ffe0-0000-1000-8000-00805f9b34fb", BleUuids.normalize("0000FFE0-0000-1000-8000-00805F9B34FB"))
        assertEquals("6e400001-b5a3-f393-e0a9-e50e24dcca9e", BleUuids.normalize("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
    }

    @Test
    fun `matches compares case-insensitively`() {
        assertTrue(BleUuids.matches("0000FFE0-0000-1000-8000-00805F9B34FB", "0000ffe0-0000-1000-8000-00805f9b34fb"))
        assertTrue(BleUuids.matches("6E400001-B5A3-F393-E0A9-E50E24DCCA9E", "6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
        assertFalse(BleUuids.matches("0000ffe0-0000-1000-8000-00805f9b34fb", "0000ffe1-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `client characteristic config descriptor is correct`() {
        assertEquals("00002902-0000-1000-8000-00805f9b34fb", BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
        assertEquals(BleUuids.CLIENT_CHARACTERISTIC_CONFIG, BleUuids.Kingsong.DESCRIPTOR)
        assertEquals(BleUuids.CLIENT_CHARACTERISTIC_CONFIG, BleUuids.Inmotion.DESCRIPTOR)
    }
}

/**
 * Tests for DiscoveredService and DiscoveredServices data classes.
 */
class DiscoveredServicesTest {

    @Test
    fun `DiscoveredService hasCharacteristic works`() {
        val service = DiscoveredService(
            uuid = "0000ffe0-0000-1000-8000-00805f9b34fb",
            characteristics = listOf(
                "0000ffe1-0000-1000-8000-00805f9b34fb",
                "0000ffe4-0000-1000-8000-00805f9b34fb"
            )
        )

        assertTrue(service.hasCharacteristic("0000ffe1-0000-1000-8000-00805f9b34fb"))
        assertTrue(service.hasCharacteristic("0000FFE4-0000-1000-8000-00805F9B34FB")) // case-insensitive
        assertFalse(service.hasCharacteristic("0000ffe9-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `DiscoveredService matchesService works case-insensitively`() {
        val service = DiscoveredService(
            uuid = "0000ffe0-0000-1000-8000-00805f9b34fb",
            characteristics = emptyList()
        )

        assertTrue(service.matchesService("0000ffe0-0000-1000-8000-00805f9b34fb"))
        assertTrue(service.matchesService("0000FFE0-0000-1000-8000-00805F9B34FB"))
        assertFalse(service.matchesService("0000ffe5-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `DiscoveredServices findService returns correct service`() {
        val services = DiscoveredServices(
            services = listOf(
                DiscoveredService("0000ffe0-0000-1000-8000-00805f9b34fb", listOf("0000ffe1-0000-1000-8000-00805f9b34fb")),
                DiscoveredService("0000ffe5-0000-1000-8000-00805f9b34fb", listOf("0000ffe9-0000-1000-8000-00805f9b34fb"))
            )
        )

        val found = services.findService("0000ffe5-0000-1000-8000-00805f9b34fb")
        assertEquals("0000ffe5-0000-1000-8000-00805f9b34fb", found?.uuid)
        assertTrue(found?.hasCharacteristic("0000ffe9-0000-1000-8000-00805f9b34fb") == true)
    }

    @Test
    fun `DiscoveredServices hasService works`() {
        val services = DiscoveredServices(
            services = listOf(
                DiscoveredService("0000ffe0-0000-1000-8000-00805f9b34fb", emptyList())
            )
        )

        assertTrue(services.hasService("0000ffe0-0000-1000-8000-00805f9b34fb"))
        assertFalse(services.hasService("0000ffe5-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `DiscoveredServices hasCharacteristic works`() {
        val services = DiscoveredServices(
            services = listOf(
                DiscoveredService(
                    "0000ffe0-0000-1000-8000-00805f9b34fb",
                    listOf("0000ffe4-0000-1000-8000-00805f9b34fb")
                )
            )
        )

        assertTrue(services.hasCharacteristic("0000ffe0-0000-1000-8000-00805f9b34fb", "0000ffe4-0000-1000-8000-00805f9b34fb"))
        assertFalse(services.hasCharacteristic("0000ffe0-0000-1000-8000-00805f9b34fb", "0000ffe1-0000-1000-8000-00805f9b34fb"))
        assertFalse(services.hasCharacteristic("0000ffe5-0000-1000-8000-00805f9b34fb", "0000ffe4-0000-1000-8000-00805f9b34fb"))
    }

    @Test
    fun `DiscoveredServices serviceUuids returns all UUIDs`() {
        val services = DiscoveredServices(
            services = listOf(
                DiscoveredService("service1", emptyList()),
                DiscoveredService("service2", emptyList()),
                DiscoveredService("service3", emptyList())
            )
        )

        assertEquals(listOf("service1", "service2", "service3"), services.serviceUuids())
    }
}
