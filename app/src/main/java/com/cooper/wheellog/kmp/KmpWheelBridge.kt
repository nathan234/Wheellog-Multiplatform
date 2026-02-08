package com.cooper.wheellog.kmp

import com.cooper.wheellog.WheelData
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.domain.WheelType
import com.cooper.wheellog.core.protocol.*
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Bridge between the existing WheelData/Adapter system and KMP decoders.
 *
 * This enables incremental migration by:
 * 1. Running KMP decoders in parallel with existing adapters
 * 2. Exposing decoded state as a StateFlow for new UI components
 * 3. Allowing comparison between old and new decoder outputs
 * 4. Eventually replacing the adapter system entirely
 *
 * ## Usage
 *
 * ```kotlin
 * // In BluetoothService or data receiving code:
 * KmpWheelBridge.instance.onDataReceived(data, wheelType)
 *
 * // In UI (Compose or Fragment):
 * val wheelState by KmpWheelBridge.instance.wheelState.collectAsState()
 * ```
 */
class KmpWheelBridge private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decoderFactory: WheelDecoderFactory = DefaultWheelDecoderFactory()

    private val _wheelState = MutableStateFlow(WheelState())
    private val _decoderConfig = MutableStateFlow(DecoderConfig())

    private var currentDecoder: WheelDecoder? = null
    private var currentWheelType: WheelType = WheelType.Unknown

    /**
     * When true, KMP state updates are synced to WheelData.
     * Enable this when using KMP_ONLY mode so the existing UI continues to work.
     */
    var syncToWheelData: Boolean = false

    /**
     * Current wheel state from KMP decoder.
     * Compare with WheelData values to validate decoder correctness.
     */
    val wheelState: StateFlow<WheelState> = _wheelState.asStateFlow()

    /**
     * Current decoder configuration.
     */
    val decoderConfig: StateFlow<DecoderConfig> = _decoderConfig.asStateFlow()

    /**
     * Whether the KMP decoder is ready (has received enough data).
     */
    val isReady: Boolean
        get() = currentDecoder?.isReady() == true

    /**
     * Update decoder configuration.
     * Call this when user settings change (e.g., mph vs km/h).
     */
    fun updateConfig(useMph: Boolean, useFahrenheit: Boolean, useCustomPercents: Boolean) {
        _decoderConfig.value = DecoderConfig(
            useMph = useMph,
            useFahrenheit = useFahrenheit,
            useCustomPercents = useCustomPercents
        )
    }

    /**
     * Set the wheel type and initialize the appropriate decoder.
     * Call this when the wheel type is detected.
     */
    fun setWheelType(wheelType: WHEEL_TYPE) {
        val kmpType = wheelType.toKmpType()
        if (kmpType == currentWheelType && currentDecoder != null) {
            return // Already initialized
        }

        currentWheelType = kmpType
        currentDecoder?.reset()
        currentDecoder = decoderFactory.createDecoder(kmpType)

        _wheelState.value = WheelState(wheelType = kmpType)

        Timber.i("KmpWheelBridge: Initialized decoder for $kmpType")
    }

    /**
     * Process incoming BLE data through the KMP decoder.
     * Call this from BluetoothService when data is received.
     *
     * @param data Raw BLE data
     * @param wheelType Current wheel type (for auto-detection or validation)
     */
    fun onDataReceived(data: ByteArray, wheelType: WHEEL_TYPE? = null) {
        // Initialize decoder if wheel type changed
        wheelType?.let { setWheelType(it) }

        val decoder = currentDecoder
        if (decoder == null) {
            Timber.w("KmpWheelBridge: No decoder initialized, ignoring data")
            return
        }

        scope.launch {
            try {
                val result = decoder.decode(data, _wheelState.value, _decoderConfig.value)
                result?.let { decoded ->
                    _wheelState.value = decoded.newState

                    // Sync to WheelData when in KMP_ONLY mode
                    if (syncToWheelData) {
                        syncStateToWheelData(decoded.newState)
                    }

                    // Log commands that would be sent (for debugging)
                    if (decoded.commands.isNotEmpty()) {
                        Timber.d("KmpWheelBridge: Decoder returned ${decoded.commands.size} commands")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "KmpWheelBridge: Error decoding data")
            }
        }
    }

    /**
     * Reset the decoder state.
     * Call this when disconnecting from a wheel.
     */
    fun reset() {
        currentDecoder?.reset()
        currentDecoder = null
        currentWheelType = WheelType.Unknown
        _wheelState.value = WheelState()
    }

    /**
     * Get init commands for the current decoder.
     * These should be sent after connecting to the wheel.
     */
    fun getInitCommands(): List<WheelCommand> {
        return currentDecoder?.getInitCommands() ?: emptyList()
    }

    /**
     * Get the keep-alive command for the current decoder.
     * Returns null if the wheel doesn't need keep-alive (e.g., Gotway, Kingsong).
     */
    fun getKeepAliveCommand(): WheelCommand? {
        return currentDecoder?.getKeepAliveCommand()
    }

    /**
     * Get the keep-alive interval in milliseconds.
     * Returns 0 if the wheel doesn't need keep-alive.
     */
    fun getKeepAliveIntervalMs(): Long {
        return currentDecoder?.keepAliveIntervalMs ?: 0
    }

    /**
     * Sync KMP WheelState to legacy WheelData.
     * Used in KMP_ONLY mode to keep the existing UI working.
     */
    private fun syncStateToWheelData(state: WheelState) {
        val wd = WheelData.getInstance() ?: return

        // Core telemetry
        wd.setSpeed(state.speed)
        wd.setVoltage(state.voltage)
        wd.setPower(state.power)
        wd.batteryLevel = state.batteryLevel
        wd.setTemperature(state.temperature)

        // Distance
        wd.totalDistance = state.totalDistance
        // wheelDistance is package-private, skip it

        // Info
        if (state.name.isNotEmpty()) wd.setName(state.name)
        if (state.model.isNotEmpty()) wd.setModel(state.model)
        if (state.serialNumber.isNotEmpty()) wd.setSerial(state.serialNumber)
        if (state.version.isNotEmpty()) wd.setVersion(state.version)

        // Additional telemetry
        wd.setTemperature2(state.temperature2)
        wd.setFanStatus(state.fanStatus)
        wd.setOutput(state.output)
        wd.setCpuLoad(state.cpuLoad)
    }

    /**
     * Compare KMP state with WheelData for validation.
     * Returns a map of fields that differ.
     */
    fun compareWithWheelData(): Map<String, Pair<Any?, Any?>> {
        val wd = WheelData.getInstance() ?: return emptyMap()
        val kmp = _wheelState.value
        val differences = mutableMapOf<String, Pair<Any?, Any?>>()

        // Compare key fields
        if (wd.speed != kmp.speed) {
            differences["speed"] = wd.speed to kmp.speed
        }
        if (wd.voltage != kmp.voltage) {
            differences["voltage"] = wd.voltage to kmp.voltage
        }
        if (wd.current != kmp.current) {
            differences["current"] = wd.current to kmp.current
        }
        if (wd.batteryLevel != kmp.batteryLevel) {
            differences["battery"] = wd.batteryLevel to kmp.batteryLevel
        }
        if (wd.temperature != kmp.temperature) {
            differences["temperature"] = wd.temperature to kmp.temperature
        }
        if (wd.totalDistance != kmp.totalDistance) {
            differences["totalDistance"] = wd.totalDistance to kmp.totalDistance
        }

        return differences
    }

    companion object {
        @Volatile
        private var INSTANCE: KmpWheelBridge? = null

        val instance: KmpWheelBridge
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE ?: KmpWheelBridge().also { INSTANCE = it }
            }

        /**
         * Convert legacy WHEEL_TYPE to KMP WheelType.
         */
        private fun WHEEL_TYPE.toKmpType(): WheelType = when (this) {
            WHEEL_TYPE.KINGSONG -> WheelType.KINGSONG
            WHEEL_TYPE.GOTWAY -> WheelType.GOTWAY
            WHEEL_TYPE.GOTWAY_VIRTUAL -> WheelType.GOTWAY_VIRTUAL
            WHEEL_TYPE.VETERAN -> WheelType.VETERAN
            WHEEL_TYPE.INMOTION -> WheelType.INMOTION
            WHEEL_TYPE.INMOTION_V2 -> WheelType.INMOTION_V2
            WHEEL_TYPE.NINEBOT -> WheelType.NINEBOT
            WHEEL_TYPE.NINEBOT_Z -> WheelType.NINEBOT_Z
            WHEEL_TYPE.Unknown -> WheelType.Unknown
        }
    }
}
