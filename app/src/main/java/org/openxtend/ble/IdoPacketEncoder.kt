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

    /**
     * GET 0x02 0x07: Query real-time heart rate from PPG sensor
     */
    fun buildGetHeartRate(): ByteArray {
        return byteArrayOf(0x02, 0x07)
    }

    /**
     * SET 0x03 0x25: Continuous Heart Rate PPG sensor control (8 bytes)
     * Activates continuous optical PPG tracking on the back of the watch.
     * 0xAA = Enabled (continuous 5 min monitoring 00:00 - 23:59), 0x55 = Disabled
     */
    fun buildSetContinuousHeartRate(enabled: Boolean = true): ByteArray {
        val switchVal = if (enabled) 0xAA.toByte() else 0x55.toByte()
        return byteArrayOf(
            0x03, 0x25,
            switchVal,
            0x05, // 5 min sampling interval
            0x00, 0x00, // start 00:00
            0x17, 0x3B  // end 23:59
        )
    }

    /**
     * SET 0x03 0x2D: Weather Display Switch (6 bytes)
     * Activates the weather screen / widget on the boAt Xtend.
     * 0xAA = Enabled, 0x55 = Disabled
     */
    fun buildSetWeatherSwitch(enabled: Boolean = true): ByteArray {
        val switchVal = if (enabled) 0xAA.toByte() else 0x55.toByte()
        return byteArrayOf(0x03, 0x2D, switchVal, 0x00, 0x00, 0x00)
    }

    /**
     * APP_WEATHER (0x0A 0x01): Push today's weather data to watch (20 bytes)
     * Format:
     * 0x0A, 0x01, [year_lo], [year_hi], [month], [day], [hour], [weather_type],
     * [current_temp_c], [max_temp_c], [min_temp_c], [humidity_pct], [uv_index], [aqi],
     * 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
     */
    fun buildWeatherPacket(
        currentTempC: Int,
        minTempC: Int,
        maxTempC: Int,
        weatherType: Int = 1, // 1: Sunny, 2: Cloudy, 3: Overcast, 4: Rain, 5: Snow, 6: Storm, 7: Fog
        humidity: Int = 50,
        uvIndex: Int = 3,
        aqi: Int = 50,
        calendar: Calendar = Calendar.getInstance()
    ): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        val month = (calendar.get(Calendar.MONTH) + 1).toByte()
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()

        val packet = ByteArray(20)
        packet[0] = 0x0A.toByte() // PROTOCOL_CMD_WEATHER
        packet[1] = 0x01.toByte() // KEY_WEATHER_TODAY
        packet[2] = (year and 0xFF).toByte()
        packet[3] = ((year shr 8) and 0xFF).toByte()
        packet[4] = month
        packet[5] = day
        packet[6] = hour
        packet[7] = weatherType.toByte()
        packet[8] = (currentTempC and 0xFF).toByte()
        packet[9] = (maxTempC and 0xFF).toByte()
        packet[10] = (minTempC and 0xFF).toByte()
        packet[11] = humidity.coerceIn(0, 100).toByte()
        packet[12] = uvIndex.coerceIn(0, 15).toByte()
        packet[13] = (aqi and 0xFF).toByte()
        // bytes 14..19 are 0x00 padding
        return packet
    }
}
