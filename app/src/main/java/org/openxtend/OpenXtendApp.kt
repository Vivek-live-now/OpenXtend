package org.openxtend

import android.app.Application
import android.os.Build
import org.openxtend.service.WatchSyncService

class OpenXtendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val serviceIntent = Intent(this, WatchSyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
