package org.freewheel.core.domain.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationConfigSerializerTest {

    @Test
    fun `round-trip preserves default config`() {
        val config = NavigationConfig()
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(config.tabs, deserialized.tabs)
    }

    @Test
    fun `round-trip preserves custom config`() {
        val config = NavigationConfig(
            tabs = listOf(
                NavigationTab.Devices, NavigationTab.Chart,
                NavigationTab.Rides, NavigationTab.Settings
            )
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(config.tabs, deserialized.tabs)
    }

    @Test
    fun `empty input returns null`() {
        assertNull(NavigationConfigSerializer.deserialize(""))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(NavigationConfigSerializer.deserialize("   "))
    }

    @Test
    fun `invalid version returns null`() {
        assertNull(NavigationConfigSerializer.deserialize("v99|tabs:DEVICES,SETTINGS"))
    }

    @Test
    fun `unknown tab names are skipped`() {
        val input = "v1|tabs:DEVICES,UNKNOWN_TAB,SETTINGS"
        val result = NavigationConfigSerializer.deserialize(input)
        assertNotNull(result)
        assertEquals(listOf(NavigationTab.Devices, NavigationTab.Settings), result.tabs)
    }

    @Test
    fun `config missing DEVICES after unknown-skip returns null`() {
        val input = "v1|tabs:UNKNOWN_TAB,SETTINGS"
        val result = NavigationConfigSerializer.deserialize(input)
        // Only SETTINGS parsed — missing DEVICES, so invalid
        assertNull(result)
    }

    @Test
    fun `serialized format starts with v2`() {
        val serialized = NavigationConfigSerializer.serialize(NavigationConfig())
        assertTrue(serialized.startsWith("v2|"))
    }

    @Test
    fun `config with only DEVICES after skipping unknowns is invalid`() {
        val input = "v1|tabs:DEVICES,FAKE1,FAKE2"
        // Only DEVICES parsed — only 1 tab, so invalid
        assertNull(NavigationConfigSerializer.deserialize(input))
    }

    @Test
    fun `empty tabs value returns null`() {
        val input = "v1|tabs:"
        assertNull(NavigationConfigSerializer.deserialize(input))
    }

    // --- v2 format ---

    @Test
    fun `v2 round-trip preserves config with custom tabs`() {
        val custom = NavigationTab.Custom(id = "racing", label = "Racing", iconName = "speed")
        val config = NavigationConfig(
            tabs = listOf(NavigationTab.Devices, custom, NavigationTab.Settings)
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(3, deserialized.tabs.size)
        assertEquals(NavigationTab.Devices, deserialized.tabs[0])
        assertEquals(NavigationTab.Settings, deserialized.tabs[2])
        val restored = deserialized.tabs[1] as NavigationTab.Custom
        assertEquals("racing", restored.id)
        assertEquals("Racing", restored.label)
        assertEquals("speed", restored.iconName)
    }

    @Test
    fun `v2 round-trip preserves multiple custom tabs`() {
        val custom1 = NavigationTab.Custom(id = "racing", label = "Racing", iconName = "speed")
        val custom2 = NavigationTab.Custom(id = "battery_view", label = "Battery", iconName = "battery_full")
        val config = NavigationConfig(
            tabs = listOf(NavigationTab.Devices, custom1, custom2, NavigationTab.Settings)
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(4, deserialized.tabs.size)
        assertEquals(2, deserialized.customTabs.size)
        assertEquals("racing", deserialized.customTabs[0].id)
        assertEquals("battery_view", deserialized.customTabs[1].id)
    }

    @Test
    fun `v1 backward compat still works`() {
        val input = "v1|tabs:DEVICES,CHART,SETTINGS"
        val result = NavigationConfigSerializer.deserialize(input)
        assertNotNull(result)
        assertEquals(3, result.tabs.size)
        assertEquals(NavigationTab.Devices, result.tabs[0])
        assertEquals(NavigationTab.Chart, result.tabs[1])
        assertEquals(NavigationTab.Settings, result.tabs[2])
    }

    @Test
    fun `v2 format structure`() {
        val custom = NavigationTab.Custom(id = "racing", label = "Racing", iconName = "speed")
        val config = NavigationConfig(
            tabs = listOf(NavigationTab.Devices, custom, NavigationTab.Settings)
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        assertTrue(serialized.startsWith("v2|"))
        assertTrue(serialized.contains("tabs:DEVICES,racing,SETTINGS"))
        assertTrue(serialized.contains("custom:racing;Racing;speed"))
    }

    @Test
    fun `v2 custom tab with special chars in label are sanitized`() {
        val custom = NavigationTab.Custom(id = "test", label = "My|Tab;Name,Here", iconName = "star")
        val config = NavigationConfig(
            tabs = listOf(NavigationTab.Devices, custom, NavigationTab.Settings)
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        val restored = deserialized.customTabs[0]
        assertEquals("MyTabNameHere", restored.label)
    }

    @Test
    fun `v2 empty tabs value returns null`() {
        val input = "v2|tabs:"
        assertNull(NavigationConfigSerializer.deserialize(input))
    }

    @Test
    fun `v2 with undefined custom tab ID in tabs list is skipped`() {
        // custom_unknown is in tabs list but not defined in custom: section
        val input = "v2|tabs:DEVICES,custom_unknown,SETTINGS"
        val result = NavigationConfigSerializer.deserialize(input)
        assertNotNull(result)
        assertEquals(2, result.tabs.size) // custom_unknown skipped
        assertEquals(NavigationTab.Devices, result.tabs[0])
        assertEquals(NavigationTab.Settings, result.tabs[1])
    }

    @Test
    fun `v2 custom tab order preserved`() {
        val custom = NavigationTab.Custom(id = "racing", label = "Racing", iconName = "speed")
        val config = NavigationConfig(
            tabs = listOf(NavigationTab.Devices, NavigationTab.Chart, custom, NavigationTab.Settings)
        )
        val serialized = NavigationConfigSerializer.serialize(config)
        val deserialized = NavigationConfigSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals("CHART", deserialized.tabs[1].id)
        assertEquals("racing", deserialized.tabs[2].id)
    }
}
