package org.openxtend.ble

import org.openxtend.model.WatchInfo

object IdoPacketDecoder {

    sealed class DecodeResult {
        data class DeviceInfoUpdate(val deviceId: Int, val firmwareVer: Int, val batteryPercent: Int, val isCharging: Boolean, val isLowPower: Boolean) : DecodeResult()
        data class BatteryUpdate(val voltageMv: Int, val batteryPercent: Int, val isCharging: Boolean, val isLowPower: Boolean) : DecodeResult()
        data class LiveDataUpdate(val steps: Int, val heartRate: Int) : DecodeResult()
        data class BindResult(val success: Boolean) : DecodeResult()
        data class TimeSyncAck(val success: Boolean) : DecodeResult()
        data class VoiceAssistantTriggered(val key: Int, val payload: ByteArray) : DecodeResult()
        data class DataUpdateNotify(val payload: ByteArray) : DecodeResult()
        object FindPhoneTriggered : DecodeResult()
        object Unknown : DecodeResult()
    }

    fun decodePacket(data: ByteArray, current: WatchInfo): DecodeResult {
        if (data.size < 2) return DecodeResult.Unknown

        val cmd = data[0].toInt() and 0xFF
        val key = data[1].toInt() and 0xFF

        when (cmd) {
            0x02 -> { // GET reply
                when (key) {
                    0x01 -> { // Device Info reply
                        if (data.size >= 8) {
                            val deviceId = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
                            val fwVer = data[4].toInt() and 0xFF
                            val battStatus = data[6].toInt() and 0xFF
                            val levelPct = data[7].toInt() and 0xFF

                            val isCharging = (battStatus == 1)
                            val isLowPower = (battStatus == 3)

                            return DecodeResult.DeviceInfoUpdate(
                                deviceId = deviceId,
                                firmwareVer = fwVer,
                                batteryPercent = levelPct,
                                isCharging = isCharging,
                                isLowPower = isLowPower
                            )
                        }
                    }
                    0x05 -> { // Battery Info reply
                        if (data.size >= 7) {
                            val voltageMv = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8)
                            val status = data[5].toInt() and 0xFF
                            val levelPct = data[6].toInt() and 0xFF

                            val isCharging = (status == 1)
                            val isLowPower = (status == 3)

                            return DecodeResult.BatteryUpdate(
                                voltageMv = voltageMv,
                                batteryPercent = levelPct,
                                isCharging = isCharging,
                                isLowPower = isLowPower
                            )
                        }
                    }
                    0x07 -> { // Real-time Heart Rate reply
                        val hr = when {
                            data.size >= 3 && (data[2].toInt() and 0xFF) in 30..220 -> data[2].toInt() and 0xFF
                            data.size >= 4 && (data[3].toInt() and 0xFF) in 30..220 -> data[3].toInt() and 0xFF
                            else -> 0
                        }
                        if (hr in 30..220) {
                            return DecodeResult.LiveDataUpdate(
                                steps = current.liveSteps,
                                heartRate = hr
                            )
                        }
                    }
                    0x69 -> { // Continuous HR live report
                        val hr = when {
                            data.size >= 3 && (data[2].toInt() and 0xFF) in 30..220 -> data[2].toInt() and 0xFF
                            data.size >= 4 && (data[3].toInt() and 0xFF) in 30..220 -> data[3].toInt() and 0xFF
                            else -> 0
                        }
                        if (hr in 30..220) {
                            return DecodeResult.LiveDataUpdate(
                                steps = current.liveSteps,
                                heartRate = hr
                            )
                        }
                    }
                    0x08 -> { // Activity reply
                        // Format: 02 08 [steps 4B LE] [calories 4B LE] [distance 4B LE]
                        if (data.size >= 6) {
                            val steps = (data[2].toInt() and 0xFF) or
                                    ((data[3].toInt() and 0xFF) shl 8) or
                                    ((data[4].toInt() and 0xFF) shl 16) or
                                    ((data[5].toInt() and 0xFF) shl 24)
                            return DecodeResult.LiveDataUpdate(
                                steps = steps,
                                heartRate = current.liveHeartRate
                            )
                        }
                    }
                    0xA0 -> { // Live Data reply: 02 A0 [steps 4B] [uptime 4B] [dist 4B] [reserved 4B] [hr 1B at index 18]
                        if (data.size >= 6) {
                            val steps = (data[2].toInt() and 0xFF) or
                                    ((data[3].toInt() and 0xFF) shl 8) or
                                    ((data[4].toInt() and 0xFF) shl 16) or
                                    ((data[5].toInt() and 0xFF) shl 24)

                            // Confirmed in IDO wire capture: HR byte is at offset 18
                            val hrFrom18 = if (data.size >= 19) data[18].toInt() and 0xFF else 0
                            val hrFrom14 = if (data.size in 15..18) data[14].toInt() and 0xFF else 0
                            val finalHr = when {
                                hrFrom18 in 30..220 -> hrFrom18
                                hrFrom14 in 30..220 -> hrFrom14
                                else -> current.liveHeartRate
                            }

                            return DecodeResult.LiveDataUpdate(
                                steps = steps,
                                heartRate = finalHr
                            )
                        }
                    }
                }
            }
            0x03 -> { // SET reply or watch events
                if (key == 0x01) {
                    return DecodeResult.TimeSyncAck(true)
                }
                if (key == 0x26 && data.size >= 3) {
                    return DecodeResult.FindPhoneTriggered
                }
            }
            0x04 -> { // BIND reply
                if (key == 0x01) {
                    val success = (data.size >= 3 && data[2].toInt() == 0) || data.size >= 2
                    return DecodeResult.BindResult(success)
                }
            }
            0x07 -> { // Watch initiated BLE event
                if (key == 0x40) {
                    return DecodeResult.DataUpdateNotify(data)
                }
            }
            0x12, 0x13 -> { // Voice Assistant / Alexa protocol events from watch
                return DecodeResult.VoiceAssistantTriggered(key, data)
            }
        }

        return DecodeResult.Unknown
    }
}
