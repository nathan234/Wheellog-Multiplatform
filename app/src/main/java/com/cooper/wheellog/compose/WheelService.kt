package com.cooper.wheellog.compose

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cooper.wheellog.R
import com.cooper.wheellog.core.protocol.DefaultWheelDecoderFactory
import com.cooper.wheellog.core.service.BleManager
import com.cooper.wheellog.core.service.ConnectionState
import com.cooper.wheellog.core.service.WheelConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WheelService : Service() {

    lateinit var bleManager: BleManager
        private set
    lateinit var connectionManager: WheelConnectionManager
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    inner class LocalBinder : Binder() {
        val service get() = this@WheelService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        bleManager = BleManager()
        bleManager.initialize(this)

        connectionManager = WheelConnectionManager(
            bleManager = bleManager,
            decoderFactory = DefaultWheelDecoderFactory(),
            scope = serviceScope
        )

        // Wire BLE data to connection manager (mirrors WheelManager.swift)
        bleManager.setDataReceivedCallback { data ->
            connectionManager.onDataReceived(data)
        }
        bleManager.setServicesDiscoveredCallback { services, deviceName ->
            connectionManager.onServicesDiscovered(services, deviceName)
            connectionManager.getConnectionInfo()?.let { info ->
                bleManager.configureForWheel(
                    info.readServiceUuid, info.readCharacteristicUuid,
                    info.writeServiceUuid, info.writeCharacteristicUuid
                )
            }
        }

        // Monitor connection state for notification updates
        serviceScope.launch {
            connectionManager.connectionState.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Disconnected"))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        // Disconnect BLE before cancelling scope to ensure the GATT connection
        // is released even if the ViewModel's coroutine was cancelled first.
        runBlocking { connectionManager.disconnect() }
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    fun shutdown() {
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WheelLog Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows wheel connection status"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, ComposeActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("WheelLog")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        val text = when (state) {
            is ConnectionState.Disconnected -> "Disconnected"
            is ConnectionState.Scanning -> "Scanning..."
            is ConnectionState.Connecting -> "Connecting..."
            is ConnectionState.DiscoveringServices -> "Discovering services..."
            is ConnectionState.Connected -> "Connected to ${state.wheelName}"
            is ConnectionState.ConnectionLost -> "Reconnecting..."
            is ConnectionState.Failed -> "Connection failed"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "wheellog_compose"
        private const val NOTIFICATION_ID = 423412
    }
}
