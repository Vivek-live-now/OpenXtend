package org.openxtend.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openxtend.R
import org.openxtend.ble.IdoBleManager
import org.openxtend.model.ConnectionStatus

class WatchSyncService : Service() {

    companion object {
        const val CHANNEL_ID = "openxtend_sync_channel"
        const val NOTIFICATION_ID = 101

        var instance: WatchSyncService? = null
            private set
    }

    private val binder = LocalBinder()
    lateinit var bleManager: IdoBleManager
        private set

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    inner class LocalBinder : Binder() {
        fun getService(): WatchSyncService = this@WatchSyncService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = IdoBleManager(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Disconnected"))

        serviceScope.launch {
            bleManager.connectionStatus.collectLatest { status ->
                val statusText = when (status) {
                    ConnectionStatus.CONNECTED -> "Connected"
                    ConnectionStatus.CONNECTING -> "Connecting..."
                    ConnectionStatus.SCANNING -> "Scanning for Xtend..."
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                }
                val notification = buildForegroundNotification(statusText)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentStatus = bleManager.connectionStatus.value
        val info = bleManager.watchInfo.value
        val text = if (currentStatus == ConnectionStatus.CONNECTED) {
            "Connected • ${info.batteryPercent}% (${info.batteryMv} mV)"
        } else {
            "Status: $currentStatus"
        }
        startForeground(NOTIFICATION_ID, buildForegroundNotification(text))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        bleManager.disconnect()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenXtend Watch Link",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains persistent connection with your boAt Xtend"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenXtend")
            .setContentText("Status: $statusText")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
