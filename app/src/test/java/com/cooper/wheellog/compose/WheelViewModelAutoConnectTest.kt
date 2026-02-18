package com.cooper.wheellog.compose

import android.app.Application
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.service.BleManager
import com.cooper.wheellog.core.service.ConnectionState
import com.cooper.wheellog.core.service.WheelConnectionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WheelViewModelAutoConnectTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var viewModel: WheelViewModel

    private lateinit var mockCm: WheelConnectionManager
    private lateinit var mockBle: BleManager
    private lateinit var mockConnectionState: MutableStateFlow<ConnectionState>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Koin is already started by WheelLog application class via Robolectric
        app = ApplicationProvider.getApplicationContext()

        // Clear preferences between tests
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()

        viewModel = WheelViewModel(app)

        mockConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        mockCm = mockk(relaxed = true) {
            every { connectionState } returns mockConnectionState
            every { wheelState } returns MutableStateFlow(WheelState())
        }
        mockBle = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun setLastMac(mac: String) {
        PreferenceManager.getDefaultSharedPreferences(app)
            .edit()
            .putString("last_mac", mac)
            .commit()
    }

    private fun getLastMac(): String {
        return PreferenceManager.getDefaultSharedPreferences(app)
            .getString("last_mac", "") ?: ""
    }

    private fun setUseReconnect(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(app)
            .edit()
            .putBoolean("use_reconnect", enabled)
            .commit()
    }

    // --- attemptStartupAutoConnect early returns ---

    @Test
    fun `attemptStartupAutoConnect does nothing when lastMac is empty`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        // Flag is set synchronously before coroutine launch — should stay false
        assertThat(viewModel.isAutoConnecting.value).isFalse()
        coVerify(exactly = 0) { mockCm.connect(any(), any()) }
    }

    @Test
    fun `attemptStartupAutoConnect does nothing when useReconnect is false`() = runTest(testDispatcher) {
        setUseReconnect(false)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isFalse()
        coVerify(exactly = 0) { mockCm.connect(any(), any()) }
    }

    @Test
    fun `attemptStartupAutoConnect does nothing when no service attached`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        // Don't call attachService

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isFalse()
    }

    // --- Successful auto-connect ---

    @Test
    fun `attemptStartupAutoConnect sets flag and calls connect`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        // Flag set synchronously before coroutine launch
        assertThat(viewModel.isAutoConnecting.value).isTrue()

        // Advance enough to run cm.connect() but not the 10s timeout
        advanceTimeBy(100)
        coVerify { mockCm.connect("AA:BB:CC:DD:EE:FF", null) }
        assertThat(viewModel.isAutoConnecting.value).isTrue()
    }

    @Test
    fun `auto-connect flag clears on timeout after 10 seconds`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isTrue()

        // Just before timeout — still true
        advanceTimeBy(9_999)
        assertThat(viewModel.isAutoConnecting.value).isTrue()

        // After timeout — cleared
        advanceTimeBy(2)
        advanceUntilIdle()
        assertThat(viewModel.isAutoConnecting.value).isFalse()
    }

    // --- Connection state clears auto-connect flag ---

    @Test
    fun `auto-connect flag clears on Connected state`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isTrue()
        advanceTimeBy(100)

        mockConnectionState.value = ConnectionState.Connected("AA:BB:CC:DD:EE:FF", "TestWheel")
        advanceUntilIdle()
        assertThat(viewModel.isAutoConnecting.value).isFalse()
    }

    @Test
    fun `auto-connect flag clears on Failed state`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isTrue()
        advanceTimeBy(100)

        mockConnectionState.value = ConnectionState.Failed("timeout")
        advanceUntilIdle()
        assertThat(viewModel.isAutoConnecting.value).isFalse()
    }

    // --- connect() persists lastMac ---

    @Test
    fun `connect persists MAC address`() = runTest(testDispatcher) {
        setLastMac("")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.connect("11:22:33:44:55:66")
        advanceUntilIdle()

        assertThat(getLastMac()).isEqualTo("11:22:33:44:55:66")
    }

    // --- disconnect() clears state ---

    @Test
    fun `disconnect clears lastMac and auto-connect flag`() = runTest(testDispatcher) {
        setUseReconnect(true)
        setLastMac("AA:BB:CC:DD:EE:FF")
        viewModel.attachService(mockCm, mockBle)
        advanceUntilIdle()

        viewModel.attemptStartupAutoConnect()
        assertThat(viewModel.isAutoConnecting.value).isTrue()

        viewModel.disconnect()
        advanceUntilIdle()

        assertThat(viewModel.isAutoConnecting.value).isFalse()
        assertThat(getLastMac()).isEmpty()
    }
}
