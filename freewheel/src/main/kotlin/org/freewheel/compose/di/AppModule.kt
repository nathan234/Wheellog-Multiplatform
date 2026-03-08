package org.freewheel.compose.di

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Vibrator
import androidx.core.content.ContextCompat.getSystemService
import androidx.preference.PreferenceManager
import org.freewheel.AppConfig
import org.freewheel.core.logging.BleCaptureLogger
import org.freewheel.core.logging.RideLogger
import org.freewheel.core.telemetry.PlatformTelemetryFileIO
import org.freewheel.core.telemetry.TelemetryFileIO
import org.freewheel.data.TripDatabase
import org.freewheel.data.TripRepository

object AppModule {
    private lateinit var appContext: Context

    fun initialize(appContext: Context) {
        this.appContext = appContext.applicationContext
    }

    val appConfig: AppConfig by lazy {
        AppConfig(appContext)
    }
    val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(appContext)
    }
    val locationManager: LocationManager? by lazy {
        getSystemService(
            appContext,
            LocationManager::class.java
        )
    }
    val notificationManager: NotificationManager? by lazy {
        getSystemService(
            appContext,
            NotificationManager::class.java
        )
    }
    val bluetoothManager: BluetoothManager? by lazy {
        getSystemService(
            appContext,
            BluetoothManager::class.java
        )
    }
    val vibrator: Vibrator? by lazy {
        getSystemService(
            appContext,
            Vibrator::class.java
        )
    }
    val tripRepository: TripRepository by lazy {
        TripRepository(TripDatabase.getDataBase(appContext).tripDao())
    }
    val rideLogger: RideLogger by lazy { RideLogger() }
    val bleCaptureLogger: BleCaptureLogger by lazy { BleCaptureLogger() }
    val telemetryFileIO: TelemetryFileIO by lazy { PlatformTelemetryFileIO() }
}