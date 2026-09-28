package org.openxtend.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.openxtend.model.NotificationApp

class NotificationCollectorService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationCollector"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        // Skip ongoing/foreground service notifications
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (isOngoing) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val typeId = mapPackageToTypeId(packageName) ?: return

        Log.d(TAG, "Forwarding notification: [$packageName] $title: $text (type=$typeId)")

        val bleManager = WatchSyncService.instance?.bleManager ?: return
        bleManager.pushNotification(typeId, title, text)
    }

    private fun mapPackageToTypeId(pkg: String): Int? {
        return when {
            pkg.contains("whatsapp", ignoreCase = true) -> NotificationApp.WHATSAPP.typeId
            pkg.contains("telegram", ignoreCase = true) -> NotificationApp.TELEGRAM.typeId
            pkg.contains("messaging", ignoreCase = true) || pkg.contains("mms", ignoreCase = true) -> NotificationApp.SMS.typeId
            pkg.contains("instagram", ignoreCase = true) -> NotificationApp.INSTAGRAM.typeId
            pkg.contains("twitter", ignoreCase = true) || pkg.contains(".x.", ignoreCase = true) -> NotificationApp.TWITTER.typeId
            pkg.contains("facebook.orca", ignoreCase = true) -> NotificationApp.MESSENGER.typeId
            pkg.contains("facebook", ignoreCase = true) -> NotificationApp.FACEBOOK.typeId
            pkg.contains("mail", ignoreCase = true) || pkg.contains("gmail", ignoreCase = true) -> NotificationApp.EMAIL.typeId
            pkg.contains("dialer", ignoreCase = true) || pkg.contains("phone", ignoreCase = true) -> NotificationApp.SMS.typeId // Fallback alert
            else -> NotificationApp.GENERIC.typeId
        }
    }
}
