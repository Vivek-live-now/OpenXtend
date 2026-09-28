package org.openxtend.model

enum class ConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

enum class NotificationApp(val typeId: Int, val title: String) {
    SMS(1, "SMS"),
    EMAIL(2, "Email"),
    WECHAT(3, "WeChat"),
    SNAPCHAT(4, "Snapchat"),
    FACEBOOK(6, "Facebook"),
    TWITTER(7, "X (Twitter)"),
    WHATSAPP(8, "WhatsApp"),
    MESSENGER(9, "Messenger"),
    INSTAGRAM(10, "Instagram"),
    LINKEDIN(11, "LinkedIn"),
    CALENDAR(12, "Calendar"),
    SKYPE(13, "Skype"),
    TELEGRAM(8, "Telegram"), // Maps to instant messaging icon
    GENERIC(1, "Notification")
}

data class WatchInfo(
    val deviceName: String = "boAt Xtend",
    val deviceAddress: String = "",
    val deviceId: Int = 0,
    val firmwareVersion: Int = 0,
    val batteryPercent: Int = 0,
    val batteryMv: Int = 0,
    val isCharging: Boolean = false,
    val isLowPower: Boolean = false,
    val isPaired: Boolean = false,
    val liveSteps: Int = 0,
    val liveHeartRate: Int = 0,
    val lastSyncEpoch: Long = 0L,
    val lastSyncStatus: String = ""
)

data class WatchSettings(
    val raiseToWake: Boolean = true,
    val musicControl: Boolean = true,
    val doNotDisturb: Boolean = false,
    val enabledNotificationApps: Set<NotificationApp> = setOf(
        NotificationApp.WHATSAPP,
        NotificationApp.SMS,
        NotificationApp.TELEGRAM
    )
)
