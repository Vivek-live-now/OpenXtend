package org.openxtend.ble

import java.util.UUID

object IdoGattAttributes {
    // Realtek / IDO Proprietary Primary GATT Service
    val SERVICE_UUID: UUID = UUID.fromString("00000af0-0000-1000-8000-00805f9b34fb")

    // Normal Command Pipe (Phone -> Watch: GET, SET, MSG, APP control)
    val CHAR_WRITE_NORMAL: UUID = UUID.fromString("00000af6-0000-1000-8000-00805f9b34fb")

    // Normal Notification Pipe (Watch -> Phone: Telemetry, Acks, Events)
    val CHAR_NOTIFY_NORMAL: UUID = UUID.fromString("00000af7-0000-1000-8000-00805f9b34fb")

    // Bulk Data Pipe (Phone -> Watch: Watch face .iwf chunks, large v3 frames)
    val CHAR_WRITE_BULK: UUID = UUID.fromString("00000af1-0000-1000-8000-00805f9b34fb")

    // Bulk Notification Pipe (Watch -> Phone: Bulk sync responses)
    val CHAR_NOTIFY_BULK: UUID = UUID.fromString("00000af2-0000-1000-8000-00805f9b34fb")

    // Standard Client Characteristic Configuration Descriptor (CCCD)
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
