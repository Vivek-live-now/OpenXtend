package org.openxtend.ble

import org.openxtend.model.WatchInfo

object IdoPacketDecoder {

    sealed class DecodeResult {
        data class DeviceInfoUpdate(val deviceId: Int, val firmwareVer: Int, val batteryPercent: Int, val isCharging: Boolean, val isLowPower: Boolean) : DecodeResult()
        data class BatteryUpdate(val voltageMv: Int, val batteryPercent: Int, val isCharging: Boolean, val isLowPower: Boolean) : DecodeResult()
        data class LiveDataUpdate(val steps: Int, val heartRate: Int) : DecodeResult()
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
                    0xA0 -> { // Live Data reply
                        // 02 A0 [steps 4B LE] [cal 4B LE] [distance 4B LE] [hr 1B]
                        if (data.size >= 6) {
                            val steps = (data[2].toInt() and 0xFF) or
                                    ((data[3].toInt() and 0xFF) shl 8) or
                                    ((data[4].toInt() and 0xFF) shl 16) or
                                    ((data[5].toInt() and 0xFF) shl 24)
                            val hr = if (data.size >= 15) data[14].toInt() and 0xFF else 0

                            return DecodeResult.LiveDataUpdate(
                                steps = steps,
                                heartRate = hr
                            )
                        }
                    }
                }
            }
            0x03 -> { // SET reply or watch events
                if (key == 0x26 && data.size >= 3) {
                    // Watch triggered "Find Phone"
                    return DecodeResult.FindPhoneTriggered
                }
            }
        }

        return DecodeResult.Unknown
    }
}
