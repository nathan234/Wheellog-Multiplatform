package org.freewheel.compose

import com.google.common.truth.Truth.assertThat
import org.freewheel.core.ble.DiscoveredService
import org.freewheel.core.ble.DiscoveredServices
import org.junit.Test

/**
 * Pass 3b Codex P2 — pin the LIVE-only gating predicate that drives
 * [WheelViewModel.discoveredServices]. Pure unit test against the
 * top-level [gateDiscoveredServices] helper so the assertion lives
 * outside [WheelViewModel]'s `SharingStarted.Eagerly` + viewModelScope
 * lifetime semantics (which are why this isn't a Robolectric test).
 */
class WheelViewModelGatingTest {

    private val sampleTopology = DiscoveredServices(
        listOf(
            DiscoveredService(
                uuid = "0000ffe0-0000-1000-8000-00805f9b34fb",
                characteristics = listOf("0000ffe1-0000-1000-8000-00805f9b34fb"),
            )
        )
    )

    @Test
    fun `LIVE mode surfaces the live topology`() {
        assertThat(gateDiscoveredServices(sampleTopology, WheelViewModel.WheelDataSource.LIVE))
            .isEqualTo(sampleTopology)
    }

    @Test
    fun `LIVE mode with no captured topology returns null`() {
        assertThat(gateDiscoveredServices(null, WheelViewModel.WheelDataSource.LIVE)).isNull()
    }

    @Test
    fun `DEMO mode masks the live topology`() {
        // The user could have connected to a live wheel earlier in the
        // session; switching to demo must not let that stale topology
        // attach itself to an unknown-wheel report.
        assertThat(gateDiscoveredServices(sampleTopology, WheelViewModel.WheelDataSource.DEMO)).isNull()
    }

    @Test
    fun `REPLAY mode masks the live topology`() {
        // Same hazard as DEMO — a replay of a past capture must not
        // surface the *current* live wheel's topology to the report.
        assertThat(gateDiscoveredServices(sampleTopology, WheelViewModel.WheelDataSource.REPLAY)).isNull()
    }
}
