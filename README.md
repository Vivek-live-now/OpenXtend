# OpenXtend

**OpenXtend** is a 100% open-source, zero-telemetry, cloud-independent Android companion application for the **boAt Xtend** smartwatch (manufactured by Shenzhen DO Intelligent Technology / iDO, platform **ID206** on the **Realtek RTL8762D** SoC).

---

## Why OpenXtend?

The official `com.boat.Xtend.two` ("boAt Wave") app and the OEM `VeryFit` app depend on closed, remote cloud infrastructure:
1. When boAt's backend expired, **Alexa broke** and the **Watch Face Market died**.
2. OEM apps upload your steps, heart rate, and device telemetry to external foreign servers.
3. Official apps frequently break background connection on newer Android versions.

**OpenXtend replaces all of that with a clean, modern, offline-first Android app:**
* **Zero Cloud Dependency:** Runs 100% locally on your phone. No logins, no tracking, no servers.
* **Instant Time Sync:** Automatically synchronizes the watch clock and calendar directly from your phone's RTC over BLE.
* **Battery Telemetry:** Shows exact battery percentage, battery status, and raw cell voltage in millivolts (`mV`).
* **Direct Notifications:** Native Android `NotificationListenerService` directly converts WhatsApp, Telegram, Signal, SMS, and incoming call alerts into raw IDO `0x05` BLE packets.
* **Hardware Controls:** Configure Raise-to-Wake (`0x03 0x28`), Music Control (`0x03 0x2A`), Find My Watch (`0x03 0x26`), and Hardware Reboot (`0xF0 0x01`).
* **Local Watch Face Sideloading:** Sideload custom `.iwf` and `.iwf.lz` watch faces directly over the bulk BLE characteristic without any cloud store.

---

## Protocol Specification

OpenXtend communicates with the watch using the **Realtek / iDO BLE Protocol**:

* **GATT Service:** `00000af0-0000-1000-8000-00805f9b34fb` (`0x0AF0`)
* **Control Write (Phone -> Watch):** `00000af6-0000-1000-8000-00805f9b34fb` (`0x0AF6`)
* **Control Notify (Watch -> Phone):** `00000af7-0000-1000-8000-00805f9b34fb` (`0x0AF7`)
* **Bulk Write (Phone -> Watch):** `00000af1-0000-1000-8000-00805f9b34fb` (`0x0AF1`)
* **Bulk Notify (Watch -> Phone):** `00000af2-0000-1000-8000-00805f9b34fb` (`0x0AF2`)

### Packet Map:

| Command | Wire Bytes (Sent to `0x0AF6`) | Description |
| :--- | :--- | :--- |
| **Bind / Pair Watch** | `04 01 F1 01 01 02 02 01 00` | Triggers pairing checkmark on screen & locks bond |
| **Get Device Info** | `02 01` | Query FW version, Device ID, Battery status |
| **Get Battery** | `02 05` | Query level % and cell voltage in mV |
| **Get Live Activity**| `02 08` | Query real-time steps & distance |
| **Get Live Data** | `02 A0` | Query current steps and live HR |
| **Set Time** | `03 01 [Y_LO] [Y_HI] [M] [D] [h] [m] [s] [dow] 00 00...` | 16-byte atomic RTC time sync |
| **Raise to Wake** | `03 28 [AA/55] 05 01 00 00 17 3B` | `AA` = Enabled, `55` = Disabled |
| **Music Control** | `03 2A [AA/55] 55` | `AA` = Enabled, `55` = Disabled |
| **Find Watch** | `03 21 01` | Triggers watch vibration motor |
| **Incoming Call** | `05 01 01 01 00 [len] [Name...]` | Shows caller name on watch screen |
| **End Call** | `05 02 00` | Dismisses incoming call alert |
| **Gemini AI / Alerts**| `05 03 [chunks] [idx] [type] [len]...` | Beams Gemini AI responses or WhatsApp |
| **Reboot Watch** | `F0 01` | Hardware reset |
| **Shutdown Watch**| `F0 03` | Power off |

---

## Architecture & Codebase

```
OpenXtend/
├── app/
│   ├── src/main/java/org/openxtend/
│   │   ├── MainActivity.kt                 # Jetpack Compose UI shell & tabs
│   │   ├── OpenXtendApp.kt                 # Application lifecycle & service launcher
│   │   ├── ble/
│   │   │   ├── IdoGattAttributes.kt        # UUID constants
│   │   │   ├── IdoBleManager.kt            # BluetoothLeScanner, connection & GATT queue
│   │   │   ├── IdoPacketEncoder.kt         # Byte-level packet builders (GET, SET, MSG)
│   │   │   └── IdoPacketDecoder.kt         # Telemetry parser (battery, steps, HR)
│   │   ├── model/
│   │   │   └── WatchModels.kt              # Connection state & telemetry dataclasses
│   │   ├── service/
│   │   │   ├── WatchSyncService.kt         # Persistent foreground service
│   │   │   └── NotificationCollectorService.kt # Android NotificationListenerService
│   │   └── ui/
│   │       ├── screens/
│   │       │   ├── DashboardScreen.kt      # Scan, Battery mV/%, Steps, HR, Sync Now
│   │       │   ├── ControlsScreen.kt       # Raise to wake, Music control, Reboot
│   │       │   ├── NotificationsScreen.kt  # Notification access setup & test alerts
│   │       │   └── WatchFaceScreen.kt      # Offline .iwf sideloading manager
│   │       └── theme/
│   │           ├── Color.kt & Theme.kt     # OLED tactical dark theme
│   └── src/test/java/org/openxtend/
│       ├── IdoPacketEncoderTest.kt         # Wire-format unit tests
│       └── IdoPacketDecoderTest.kt         # Telemetry decoding unit tests
```

---

## Building & Installing

### Requirements:
* Android SDK 34 (compileSdk 34, minSdk 26)
* JDK 17+
* Gradle 8.3.0

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests:
```bash
./gradlew test
```
