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
     * APP_WEATHER (0x0A 0x01): Push today's weather data to watch (EXACTLY 18 bytes matching VeryFit wire capture)
     * Format:
     * 0A 01 [type] [current_temp_c] [max_temp_c] [min_temp_c] [humidity] [uv] [aqi]
     *       [day1_type] [day1_max] [day1_min]
     *       [day2_type] [day2_max] [day2_min]
     *       [day3_type] [day3_max] [day3_min]
     */
    fun buildWeatherDataPacket(
        currentTempC: Int,
        maxTempC: Int,
        minTempC: Int,
        weatherType: Int = 1, // 1: Sunny, 2: Cloudy, 3: Overcast, 4: Rain, 5: Snow, 6: Storm, 7: Fog
        humidity: Int = 50,
        uvIndex: Int = 1,
        aqi: Int = 0,
        day1Type: Int = weatherType,
        day1Max: Int = maxTempC,
        day1Min: Int = minTempC,
        day2Type: Int = weatherType,
        day2Max: Int = maxTempC,
        day2Min: Int = minTempC,
        day3Type: Int = weatherType,
        day3Max: Int = maxTempC,
        day3Min: Int = minTempC
    ): ByteArray {
        val packet = ByteArray(18)
        packet[0] = 0x0A.toByte() // PROTOCOL_CMD_WEATHER
        packet[1] = 0x01.toByte() // KEY_WEATHER_TODAY
        packet[2] = weatherType.toByte()
        packet[3] = (currentTempC and 0xFF).toByte()
        packet[4] = (maxTempC and 0xFF).toByte()
        packet[5] = (minTempC and 0xFF).toByte()
        packet[6] = humidity.coerceIn(0, 100).toByte()
        packet[7] = uvIndex.coerceIn(0, 15).toByte()
        packet[8] = (aqi and 0xFF).toByte()

        // Day 1 forecast
        packet[9] = day1Type.toByte()
        packet[10] = (day1Max and 0xFF).toByte()
        packet[11] = (day1Min and 0xFF).toByte()

        // Day 2 forecast
        packet[12] = day2Type.toByte()
        packet[13] = (day2Max and 0xFF).toByte()
        packet[14] = (day2Min and 0xFF).toByte()

        // Day 3 forecast
        packet[15] = day3Type.toByte()
        packet[16] = (day3Max and 0xFF).toByte()
        packet[17] = (day3Min and 0xFF).toByte()

        return packet
    }

    /**
     * APP_WEATHER_CITY_NAME (0x0A 0x02): Push city name to watch (EXACTLY 20 bytes matching VeryFit wire capture)
     * Format: 0A 02 <city_len> <city_ascii...> <00 00 ...>
     */
    fun buildWeatherCityPacket(cityName: String): ByteArray {
        val cityBytes = cityName.toByteArray(StandardCharsets.UTF_8).take(17).toByteArray()
        val packet = ByteArray(20)
        packet[0] = 0x0A.toByte() // PROTOCOL_CMD_WEATHER
        packet[1] = 0x02.toByte() // KEY_WEATHER_CITY_NAME
        packet[2] = cityBytes.size.toByte()
        System.arraycopy(cityBytes, 0, packet, 3, cityBytes.size)
        return packet
    }

    /**
     * SET 0x12 0x24: Enable Voice Assistant / Alexa state on watch (5 bytes)
     * Tells the watch that the companion app authorizes voice assistant.
     */
    fun buildSetAlexaVoiceState(enabled: Boolean = true): ByteArray {
        val switchVal = if (enabled) 0x01.toByte() else 0x00.toByte()
        return byteArrayOf(0x12, 0x24, switchVal, 0x00, 0x00)
    }

    /**
     * SET 0x12 0x21: Alexa Operational Ready handshake (4 bytes)
     */
    fun buildSetAlexaReady(): ByteArray {
        return byteArrayOf(0x12, 0x21, 0x01, 0x00)
    }

    /**
     * ACK 0x12 [key]: Acknowledge watch voice event
     */
    fun buildAlexaVoiceAck(key: Int): ByteArray {
        return byteArrayOf(0x12, key.toByte(), 0x00, 0x00)
    }

    /**
     * CMD 0x13 0x01: Send Voice Assistant text answer to watch screen (chunks)
     */
    fun buildVoiceAssistantResponse(replyText: String): List<ByteArray> {
        val textBytes = replyText.toByteArray(StandardCharsets.UTF_8).take(180).toByteArray()
        val chunkSize = 16
        val totalChunks = (textBytes.size + chunkSize - 1) / chunkSize
        val frames = mutableListOf<ByteArray>()

        for (i in 0 until totalChunks) {
            val frame = ByteArray(20)
            frame[0] = 0x13.toByte() // CMD_VOICE
            frame[1] = 0x01.toByte()
            frame[2] = totalChunks.toByte()
            frame[3] = (i + 1).toByte()

            val offset = i * chunkSize
            val remaining = textBytes.size - offset
            val copyLen = remaining.coerceAtMost(chunkSize)
            System.arraycopy(textBytes, offset, frame, 4, copyLen)
            frames.add(frame)
        }

        return frames
    }

    /**
     * CMD 0x13 0x02: Send Alexa UI State to watch screen
     * state: 0x01 = Listening (wave), 0x02 = Thinking (spinner), 0x00 = Idle/Dismiss, 0x03 = Error
     */
    fun buildAlexaState(state: Int): ByteArray {
        return byteArrayOf(0x13, 0x02, state.toByte(), 0x00)
    }
}
