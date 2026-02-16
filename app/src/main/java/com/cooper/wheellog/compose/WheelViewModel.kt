package com.cooper.wheellog.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.wheellog.AppConfig
import com.cooper.wheellog.core.domain.WheelState
import com.cooper.wheellog.core.logging.RideLogger
import com.cooper.wheellog.core.telemetry.TelemetryBuffer
import com.cooper.wheellog.core.telemetry.TelemetrySample
import com.cooper.wheellog.core.service.BleDevice
import com.cooper.wheellog.core.service.BleManager
import com.cooper.wheellog.core.service.ConnectionState
import com.cooper.wheellog.core.service.DemoDataProvider
import com.cooper.wheellog.core.service.WheelConnectionManager
import com.cooper.wheellog.core.domain.SettingsCommandId
import com.cooper.wheellog.data.TripDataDbEntry
import com.cooper.wheellog.data.TripDatabase
import com.cooper.wheellog.data.TripRepository
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AlarmType {
    SPEED_1, SPEED_2, SPEED_3, CURRENT, TEMPERATURE, BATTERY
}

class WheelViewModel(application: Application) : AndroidViewModel(application) {

    val appConfig: AppConfig = AppConfig(application)
    private val tripRepository: TripRepository

    private val demoDataProvider = DemoDataProvider()

    // Service references (set via attachService/detachService)
    private var connectionManager: WheelConnectionManager? = null
    private var bleManager: BleManager? = null
    private var stateCollectionJob: Job? = null
    private var connectionCollectionJob: Job? = null

    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Demo mode
    private val _isDemo = MutableStateFlow(false)
    val isDemo: StateFlow<Boolean> = _isDemo.asStateFlow()

    // Scanning
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Discovered devices
    data class DiscoveredDevice(
        val name: String,
        val address: String,
        val rssi: Int
    )
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Real wheel state from WheelConnectionManager
    private val _realWheelState = MutableStateFlow(WheelState())

    // Wheel state — combines real and demo sources
    val wheelState: StateFlow<WheelState> = combine(
        _isDemo,
        _realWheelState,
        demoDataProvider.wheelState
    ) { demo, realState, demoState ->
        if (demo) demoState else realState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WheelState())

    // Alarms
    private val _activeAlarms = MutableStateFlow<Set<AlarmType>>(emptySet())
    val activeAlarms: StateFlow<Set<AlarmType>> = _activeAlarms.asStateFlow()

    // Telemetry buffer for charts (shared KMP)
    val telemetryBuffer = TelemetryBuffer()

    // GPS speed (m/s from FusedLocation, converted to km/h for display)
    private val _gpsSpeedKmh = MutableStateFlow(0.0)
    val gpsSpeedKmh: StateFlow<Double> = _gpsSpeedKmh.asStateFlow()

    // Expose samples as StateFlow for backward compatibility with ChartScreen
    private val _telemetrySamples = MutableStateFlow<List<TelemetrySample>>(emptyList())
    val telemetrySamples: StateFlow<List<TelemetrySample>> = _telemetrySamples.asStateFlow()

    // Logging
    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    // Light state
    private val _isLightOn = MutableStateFlow(false)
    val isLightOn: StateFlow<Boolean> = _isLightOn.asStateFlow()

    init {
        val db = TripDatabase.getDataBase(application)
        tripRepository = TripRepository(db.tripDao())
        startTelemetryBuffering()
        startAlarmMonitoring()
    }

    // --- Service binding ---

    fun attachService(cm: WheelConnectionManager, ble: BleManager) {
        connectionManager = cm
        bleManager = ble

        stateCollectionJob = viewModelScope.launch {
            cm.wheelState.collect { _realWheelState.value = it }
        }
        connectionCollectionJob = viewModelScope.launch {
            cm.connectionState.collect { state ->
                if (!_isDemo.value) {
                    _connectionState.value = state
                }
            }
        }
    }

    fun detachService() {
        stateCollectionJob?.cancel()
        connectionCollectionJob?.cancel()
        stateCollectionJob = null
        connectionCollectionJob = null
        connectionManager = null
        bleManager = null
    }

    // --- Demo mode ---

    fun startDemo() {
        _isDemo.value = true
        _connectionState.value = ConnectionState.Connected("demo", "Demo Wheel")
        demoDataProvider.start(viewModelScope)
    }

    fun stopDemo() {
        if (rideLogger.isLogging) stopLogging()
        demoDataProvider.stop()
        _isDemo.value = false
        _connectionState.value = ConnectionState.Disconnected
        telemetryBuffer.clear()
        _telemetrySamples.value = emptyList()
    }

    // --- BLE operations ---

    fun startScan() {
        val ble = bleManager ?: return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        viewModelScope.launch {
            ble.startScan { device ->
                addDiscoveredDevice(device)
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
        viewModelScope.launch {
            bleManager?.stopScan()
        }
    }

    private fun addDiscoveredDevice(device: BleDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val existing = current.indexOfFirst { it.address == device.address }
        val discovered = DiscoveredDevice(
            name = device.name ?: "Unknown",
            address = device.address,
            rssi = device.rssi
        )
        if (existing >= 0) {
            current[existing] = discovered
        } else {
            current.add(discovered)
        }
        _discoveredDevices.value = current.sortedByDescending { it.rssi }
    }

    fun connect(address: String) {
        val cm = connectionManager ?: return
        _isScanning.value = false
        viewModelScope.launch {
            bleManager?.stopScan()
            cm.connect(address)
        }
    }

    fun disconnect() {
        if (_isDemo.value) {
            stopDemo()
            return
        }
        if (rideLogger.isLogging) stopLogging()
        viewModelScope.launch {
            connectionManager?.disconnect()
        }
        telemetryBuffer.clear()
        _telemetrySamples.value = emptyList()
    }

    // --- Wheel commands ---

    fun wheelBeep() {
        viewModelScope.launch {
            connectionManager?.wheelBeep()
        }
    }

    fun toggleLight() {
        val newState = !_isLightOn.value
        _isLightOn.value = newState
        viewModelScope.launch {
            connectionManager?.toggleLight(newState)
        }
    }

    fun setPedalsMode(mode: Int) {
        viewModelScope.launch {
            connectionManager?.setPedalsMode(mode)
        }
    }

    fun executeWheelCommand(commandId: SettingsCommandId, intValue: Int = 0, boolValue: Boolean = false) {
        viewModelScope.launch {
            connectionManager?.executeCommand(commandId, intValue, boolValue)
        }
    }

    // --- Logging ---

    private val rideLogger = RideLogger()

    fun toggleLogging() {
        if (rideLogger.isLogging) {
            stopLogging()
        } else {
            startLogging()
        }
    }

    private fun startLogging() {
        val app = getApplication<Application>()
        val ridesDir = File(app.getExternalFilesDir(null), "rides")
        ridesDir.mkdirs()
        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
        val fileName = "${sdf.format(Date())}.csv"
        val filePath = File(ridesDir, fileName).absolutePath
        val includeGps = appConfig.logLocationData

        val now = System.currentTimeMillis()
        if (rideLogger.start(filePath, includeGps, now)) {
            _isLogging.value = true
            startLogSampling()
        }
    }

    private fun stopLogging() {
        logSamplingJob?.cancel()
        logSamplingJob = null
        val state = wheelState.value
        val metadata = rideLogger.stop(System.currentTimeMillis(), state.totalDistance)
        _isLogging.value = false

        if (metadata != null) {
            viewModelScope.launch {
                tripRepository.insertNewData(
                    TripDataDbEntry(
                        fileName = metadata.fileName,
                        start = (metadata.startTimeMillis / 1000).toInt(),
                        duration = (metadata.durationSeconds / 60).toInt(),
                        maxSpeed = metadata.maxSpeedKmh.toFloat(),
                        avgSpeed = metadata.avgSpeedKmh.toFloat(),
                        maxCurrent = metadata.maxCurrentA.toFloat(),
                        maxPower = metadata.maxPowerW.toFloat(),
                        maxPwm = metadata.maxPwmPercent.toFloat(),
                        distance = metadata.distanceMeters.toInt(),
                        consumptionTotal = metadata.consumptionWh.toFloat(),
                        consumptionByKm = metadata.consumptionWhPerKm.toFloat()
                    )
                )
            }
        }
    }

    private var logSamplingJob: Job? = null

    private fun startLogSampling() {
        logSamplingJob = viewModelScope.launch {
            wheelState.collect { state ->
                if (rideLogger.isLogging) {
                    rideLogger.writeSample(state, null, System.currentTimeMillis())
                }
            }
        }
    }

    suspend fun loadTrips(): List<TripDataDbEntry> {
        return tripRepository.getAllData().sortedByDescending { it.start }
    }

    fun deleteTrip(trip: TripDataDbEntry, context: Context) {
        viewModelScope.launch {
            tripRepository.removeDataById(trip.id.toLong())
            val ridesDir = File(context.getExternalFilesDir(null), "rides")
            val csvFile = File(ridesDir, trip.fileName)
            if (csvFile.exists()) csvFile.delete()
        }
    }

    // --- Telemetry buffering ---

    private fun startTelemetryBuffering() {
        viewModelScope.launch {
            wheelState.collect { state ->
                if (state.speed != 0 || state.voltage != 0) {
                    val sample = TelemetrySample(
                        timestampMs = System.currentTimeMillis(),
                        speedKmh = state.speedKmh,
                        voltageV = state.voltageV,
                        currentA = state.currentA,
                        powerW = state.powerW,
                        temperatureC = state.temperatureC.toDouble(),
                        batteryPercent = state.batteryLevel.toDouble(),
                        pwmPercent = state.pwmPercent,
                        gpsSpeedKmh = _gpsSpeedKmh.value
                    )
                    if (telemetryBuffer.addSampleIfNeeded(sample)) {
                        _telemetrySamples.value = telemetryBuffer.samples
                    }
                }
            }
        }
    }

    /** Call from Activity/Service when GPS location updates arrive. */
    fun updateGpsSpeed(speedMs: Float) {
        _gpsSpeedKmh.value = speedMs.toDouble() * 3.6
    }

    // --- Alarm monitoring ---

    private val alarmCooldowns = mutableMapOf<AlarmType, Long>()
    private val ALARM_COOLDOWN_MS = 5000L

    private fun startAlarmMonitoring() {
        viewModelScope.launch {
            wheelState.collect { state ->
                if (!appConfig.alarmsEnabled) {
                    if (_activeAlarms.value.isNotEmpty()) {
                        _activeAlarms.value = emptySet()
                    }
                    return@collect
                }

                val now = System.currentTimeMillis()
                val alarms = mutableSetOf<AlarmType>()

                val speedKmh = state.speedKmh
                checkAlarm(AlarmType.SPEED_1, speedKmh >= appConfig.alarm1Speed && appConfig.alarm1Speed > 0, now, alarms)
                checkAlarm(AlarmType.SPEED_2, speedKmh >= appConfig.alarm2Speed && appConfig.alarm2Speed > 0, now, alarms)
                checkAlarm(AlarmType.SPEED_3, speedKmh >= appConfig.alarm3Speed && appConfig.alarm3Speed > 0, now, alarms)

                val currentA = kotlin.math.abs(state.currentA)
                checkAlarm(AlarmType.CURRENT, currentA >= appConfig.alarmCurrent && appConfig.alarmCurrent > 0, now, alarms)

                checkAlarm(AlarmType.TEMPERATURE, state.temperatureC >= appConfig.alarmTemperature && appConfig.alarmTemperature > 0, now, alarms)

                checkAlarm(AlarmType.BATTERY, state.batteryLevel <= appConfig.alarmBattery && appConfig.alarmBattery > 0 && state.batteryLevel > 0, now, alarms)

                _activeAlarms.value = alarms
            }
        }
    }

    private fun checkAlarm(type: AlarmType, triggered: Boolean, now: Long, alarms: MutableSet<AlarmType>) {
        if (triggered) {
            val lastFired = alarmCooldowns[type] ?: 0
            if (now - lastFired >= ALARM_COOLDOWN_MS) {
                alarmCooldowns[type] = now
            }
            alarms.add(type)
        }
    }
}
