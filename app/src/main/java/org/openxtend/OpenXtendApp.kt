package org.openxtend

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import org.openxtend.service.WatchSyncService

class OpenXtendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val serviceIntent = Intent(this, WatchSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("OpenXtendApp", "Deferred WatchSyncService start: ${e.message}")
        }
    }
}
