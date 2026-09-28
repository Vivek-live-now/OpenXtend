package org.openxtend

import android.app.Application
import android.content.Intent
import org.openxtend.service.WatchSyncService

class OpenXtendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val serviceIntent = Intent(this, WatchSyncService::class.java)
        startService(serviceIntent)
    }
}
