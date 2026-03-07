package org.freewheel.core.domain.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationTabTest {

    @Test
    fun `DEVICES and SETTINGS are the only required tabs`() {
        val required = NavigationTab.entries.filter { it.isRequired }
        assertEquals(setOf(NavigationTab.DEVICES, NavigationTab.SETTINGS), required.toSet())
    }

    @Test
    fun `all tabs have unique routes`() {
        val routes = NavigationTab.entries.map { it.route }
        assertEquals(routes.toSet().size, routes.size)
    }

    @Test
    fun `all tabs have non-empty labels`() {
        for (tab in NavigationTab.entries) {
            assertTrue(tab.label.isNotBlank(), "${tab.name} should have a label")
        }
    }

    @Test
    fun `all tabs have non-empty icon names`() {
        for (tab in NavigationTab.entries) {
            assertTrue(tab.iconName.isNotBlank(), "${tab.name} should have an icon name")
        }
    }

    @Test
    fun `there are 6 navigation tabs`() {
        assertEquals(6, NavigationTab.entries.size)
    }

    @Test
    fun `DEVICES route is devices`() {
        assertEquals("devices", NavigationTab.DEVICES.route)
    }

    @Test
    fun `CHART route is chart`() {
        assertEquals("chart", NavigationTab.CHART.route)
    }
}
