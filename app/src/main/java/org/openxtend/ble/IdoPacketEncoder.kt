package org.openxtend.ble

import java.nio.charset.StandardCharsets
import java.util.Calendar

object IdoPacketEncoder {

    /**
     * GET 0x02 0x01: Request Device Info (firmware, device ID, battery status)
     */
    fun buildGetDeviceInfo(): ByteArray {
        return byteArrayOf(0x02, 0x01)
    }

    /**
     * GET 0x02 0x05: Request Battery Status (voltage in mV, charging status, level %)
     */
    fun buildGetBatteryInfo(): ByteArray {
        return byteArrayOf(0x02, 0x05)
    }

    /**
     * GET 0x02 0xA0: Request Live Telemetry (real-time steps and heart rate)
     */
    fun buildGetLiveData(): ByteArray {
        return byteArrayOf(0x02, 0xA0.toByte())
    }

    /**
     * GET 0x02 0x08: Query Activity / Steps
     */
    fun buildGetLiveActivity(): ByteArray {
        return byteArrayOf(0x02, 0x08)
    }

    /**
     * BIND 0x04 0x01: Start VeryFit/iDO pairing handshake
     * Packet: 04 01 F1 01 01 02 02 01 00 (9 bytes)
     * Triggers the pairing checkmark prompt on the boAt Xtend screen.
     */
    fun buildBindStart(): ByteArray {
        return byteArrayOf(
            0x04, 0x01,
            0xF1.toByte(),
            0x01, 0x01, 0x02, 0x02, 0x01, 0x00
        )
    }

    /**
     * UNBIND 0x04 0x02: Unpair watch
     */
    fun buildUnbind(): ByteArray {
        return byteArrayOf(0x04, 0x02)
    }

    /**
     * SET 0x03 0x01: Sync Time (16 bytes total)
     * Format: 03 01 [Y_LO] [Y_HI] [MONTH] [DAY] [HOUR] [MIN] [SEC] [WEEKDAY] 00 00 00 00 00 00
     */
    fun buildSetTime(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val min = calendar.get(Calendar.MINUTE).toByte()
        val sec = calendar.get(Calendar.SECOND).toByte()
        val calDay = calendar.get(Calendar.DAY_OF_WEEK)
        // Convert to 1 (Mon) - 7 (Sun)
        val weekday = (if (calDay == Calendar.SUNDAY) 7 else calDay - 1).toByte()

        return byteArrayOf(
            0x03,
            0x01,
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            month,
            day,
            hour,
            min,
            sec,
            weekday,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    }

    /**
     * SET 0x03 0x28: Raise-to-wake gesture toggle (9 bytes)
     * 0xAA = Enabled, 0x55 = Disabled
     */
    fun buildSetRaiseToWake(enabled: Boolean): ByteArray {
        val switchVal = if (enabled) 0xAA.toByte() else 0x55.toByte()
        return byteArrayOf(
            0x03, 0x28,
            switchVal,
            0x05, 0x01, 0x00, 0x00, 0x17, 0x3B
        )
    }

    /**
     * SET 0x03 0x2A: Music Control switch (4 bytes)
     * 0xAA = Enabled, 0x55 = Disabled
     */
    fun buildSetMusicControl(enabled: Boolean): ByteArray {
        val switchVal = if (enabled) 0xAA.toByte() else 0x55.toByte()
        return byteArrayOf(0x03, 0x2A, switchVal, 0x55)
    }

    /**
     * SET 0x03 0x26: Find My Phone (Watch -> Phone)
     */
    fun buildFindPhone(timeoutSeconds: Int = 30): ByteArray {
        return byteArrayOf(0x03, 0x26, 0x01, timeoutSeconds.toByte())
    }

    /**
     * SET 0x03 0x21: Lost Find / Find Watch (Phone -> Watch vibration & buzzer)
     */
    fun buildFindWatch(): ByteArray {
        return byteArrayOf(0x03, 0x21, 0x01)
    }

    /**
     * Reboot MCU
     */
    fun buildReboot(): ByteArray {
        return byteArrayOf(0xF0.toByte(), 0x01)
    }

    /**
     * Shutdown MCU
     */
    fun buildShutdown(): ByteArray {
        return byteArrayOf(0xF0.toByte(), 0x03)
    }

    /**
     * MSG 0x05 0x01: Incoming Call Notification
     */
    fun buildCallAlert(callerName: String): ByteArray {
        val nameBytes = callerName.toByteArray(StandardCharsets.UTF_8).take(14).toByteArray()
        val payload = ByteArray(16)
        payload[0] = 0x00 // phone number length
        payload[1] = nameBytes.size.toByte()
        System.arraycopy(nameBytes, 0, payload, 2, nameBytes.size)

        val packet = ByteArray(20)
        packet[0] = 0x05 // MSG
        packet[1] = 0x01 // CALL
        packet[2] = 0x01 // total chunks
        packet[3] = 0x01 // serial
        System.arraycopy(payload, 0, packet, 4, 16)
        return packet
    }

    /**
     * MSG 0x05 0x02: Dismiss / End Call on Watch
     */
    fun buildCallEnd(): ByteArray {
        return byteArrayOf(0x05, 0x02, 0x00)
    }

    /**
     * MSG 0x05 0x03: App Notification (WhatsApp, SMS, Telegram, etc.)
     * Returns a list of 20-byte BLE frames (4 bytes header + 16 bytes chunk).
     */
    fun buildNotificationPackets(
        typeId: Int,
        sender: String,
        message: String
    ): List<ByteArray> {
        val senderBytes = sender.toByteArray(StandardCharsets.UTF_8).take(32).toByteArray()
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8).take(200).toByteArray()

        // Construct contiguous payload:
        // [type (1B), total_data_len (1B), phone_len (1B), contact_len (1B), sender..., message...]
        val totalDataLen = (4 + senderBytes.size + messageBytes.size).coerceAtMost(255)
        val fullPayload = ByteArray(totalDataLen)

        fullPayload[0] = typeId.toByte()
        fullPayload[1] = (senderBytes.size + messageBytes.size).toByte()
        fullPayload[2] = 0x00 // No phone number
        fullPayload[3] = senderBytes.size.toByte()

        System.arraycopy(senderBytes, 0, fullPayload, 4, senderBytes.size)
        System.arraycopy(messageBytes, 0, fullPayload, 4 + senderBytes.size, messageBytes.size)

        // Split fullPayload into 16-byte chunks
        val chunkSize = 16
        val totalChunks = (fullPayload.size + chunkSize - 1) / chunkSize
        val frames = mutableListOf<ByteArray>()

        for (i in 0 until totalChunks) {
            val frame = ByteArray(20)
            frame[0] = 0x05 // PROTOCOL_CMD_MSG
            frame[1] = 0x03 // KEY_MSG_MSG
            frame[2] = totalChunks.toByte()
            frame[3] = (i + 1).toByte() // 1-based serial index

            val offset = i * chunkSize
            val remaining = fullPayload.size - offset
            val copyLen = remaining.coerceAtMost(chunkSize)
            System.arraycopy(fullPayload, offset, frame, 4, copyLen)

            frames.add(frame)
        }

        return frames
    }
}
