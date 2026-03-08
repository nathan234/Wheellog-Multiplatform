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
}