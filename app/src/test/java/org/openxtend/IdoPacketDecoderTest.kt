package org.openxtend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openxtend.ble.IdoPacketDecoder
import org.openxtend.model.WatchInfo

class IdoPacketDecoderTest {

    @Test
    fun testDecodeDeviceInfo() {
        // Mock packet: [0x02, 0x01, dev_lo, dev_hi, fw_ver, mode, batt_status, level_pct]
        // Device ID = 0x0214, FW = 17, Status = 1 (charging), Level = 85%
        val mockData = byteArrayOf(
            0x02, 0x01,
            0x14, 0x02,
            0x11, // 17
            0x00,
            0x01, // charging
            0x55  // 85%
        )

        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.DeviceInfoUpdate)

        val update = result as IdoPacketDecoder.DecodeResult.DeviceInfoUpdate
        assertEquals(0x0214, update.deviceId)
        assertEquals(17, update.firmwareVer)
        assertEquals(85, update.batteryPercent)
        assertTrue(update.isCharging)
    }

    @Test
    fun testDecodeBatteryInfo() {
        // Mock packet: [0x02, 0x05, type, v_lo, v_hi, status, level_pct]
        // 3850 mV = 0x0F0A -> low 0x0A, high 0x0F, level = 80%, status = 0
        val mockData = byteArrayOf(
            0x02, 0x05,
            0x00,
            0x0A, 0x0F,
            0x00,
            0x50 // 80%
        )

        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.BatteryUpdate)

        val update = result as IdoPacketDecoder.DecodeResult.BatteryUpdate
        assertEquals(3850, update.voltageMv)
        assertEquals(80, update.batteryPercent)
    }

    @Test
    fun testDecodeLiveData() {
        // Mock packet: 02 A0 [steps 4B LE = 5432 = 0x1538] ... [hr = 78]
        val mockData = ByteArray(16)
        mockData[0] = 0x02
        mockData[1] = 0xA0.toByte()
        mockData[2] = 0x38
        mockData[3] = 0x15
        mockData[4] = 0x00
        mockData[5] = 0x00
        mockData[14] = 78 // HR 78 bpm

        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.LiveDataUpdate)

        val update = result as IdoPacketDecoder.DecodeResult.LiveDataUpdate
        assertEquals(5432, update.steps)
        assertEquals(78, update.heartRate)
    }

    @Test
    fun testDecodeBindSuccess() {
        val mockData = byteArrayOf(0x04, 0x01, 0x00, 0x00)
        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.BindResult)
        assertTrue((result as IdoPacketDecoder.DecodeResult.BindResult).success)
    }

    @Test
    fun testDecodeHeartRateDirect() {
        val mockData = byteArrayOf(0x02, 0x07, 72)
        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo(liveSteps = 1000))
        assertTrue(result is IdoPacketDecoder.DecodeResult.LiveDataUpdate)
        val update = result as IdoPacketDecoder.DecodeResult.LiveDataUpdate
        assertEquals(72, update.heartRate)
        assertEquals(1000, update.steps)
    }

    @Test
    fun testDecodeActivitySteps() {
        // Steps = 2500 = 0x09C4 -> low 0xC4, high 0x09
        // Bytes 6..9 are calories (80 kcal), which must NOT overwrite liveHeartRate
        val mockData = byteArrayOf(0x02, 0x08, 0xC4.toByte(), 0x09, 0x00, 0x00, 80)
        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo(liveHeartRate = 72))
        assertTrue(result is IdoPacketDecoder.DecodeResult.LiveDataUpdate)
        val update = result as IdoPacketDecoder.DecodeResult.LiveDataUpdate
        assertEquals(2500, update.steps)
        assertEquals(72, update.heartRate)
    }

    @Test
    fun testDecodeLiveDataSeparatesCaloriesAndHeartRate() {
        // Mock packet: 02 A0 [steps 4B LE = 1000] [cal 4B LE = 95 kcal] [dist 4B LE] [hr 1B at index 14 = 76 bpm]
        val mockData = ByteArray(16)
        mockData[0] = 0x02
        mockData[1] = 0xA0.toByte()
        mockData[2] = 0xE8.toByte() // 1000 steps = 0x03E8
        mockData[3] = 0x03
        mockData[6] = 95 // 95 kcal (must NOT be treated as HR!)
        mockData[14] = 76 // 76 bpm real heart rate

        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.LiveDataUpdate)
        val update = result as IdoPacketDecoder.DecodeResult.LiveDataUpdate
        assertEquals(1000, update.steps)
        assertEquals(76, update.heartRate)
    }

    @Test
    fun testDecodeLiveData20ByteWithHeartRateAtByte18() {
        // Mock 20-byte packet: 02 A0 [steps 4B LE = 3500] [uptime 4B] [dist 4B] [reserved 4B] [hr 1B at index 18 = 82] [reserved 1B]
        val mockData = ByteArray(20)
        mockData[0] = 0x02
        mockData[1] = 0xA0.toByte()
        // 3500 steps = 0x0DAC
        mockData[2] = 0xAC.toByte()
        mockData[3] = 0x0D
        mockData[4] = 0x00
        mockData[5] = 0x00
        // HR at index 18
        mockData[18] = 82

        val result = IdoPacketDecoder.decodePacket(mockData, WatchInfo())
        assertTrue(result is IdoPacketDecoder.DecodeResult.LiveDataUpdate)
        val update = result as IdoPacketDecoder.DecodeResult.LiveDataUpdate
        assertEquals(3500, update.steps)
        assertEquals(82, update.heartRate)
    }

    @Test
    fun testWeatherEncoderMatchesWireSpecification() {
        val weatherPacket = org.openxtend.ble.IdoPacketEncoder.buildWeatherDataPacket(
            currentTempC = 27,
            maxTempC = 32,
            minTempC = 22,
            weatherType = 1,
            humidity = 60
        )
        // Must be exactly 18 bytes
        assertEquals(18, weatherPacket.size)
        assertEquals(0x0A.toByte(), weatherPacket[0])
        assertEquals(0x01.toByte(), weatherPacket[1])
        assertEquals(1.toByte(), weatherPacket[2]) // type
        assertEquals(27.toByte(), weatherPacket[3]) // cur
        assertEquals(32.toByte(), weatherPacket[4]) // max
        assertEquals(22.toByte(), weatherPacket[5]) // min
        assertEquals(60.toByte(), weatherPacket[6]) // humidity

        val cityPacket = org.openxtend.ble.IdoPacketEncoder.buildWeatherCityPacket("Delhi")
        // Must be exactly 20 bytes
        assertEquals(20, cityPacket.size)
        assertEquals(0x0A.toByte(), cityPacket[0])
        assertEquals(0x02.toByte(), cityPacket[1])
        assertEquals(5.toByte(), cityPacket[2]) // len("Delhi")
        assertEquals('D'.code.toByte(), cityPacket[3])
    }

    @Test
    fun testVoiceAssistantPackets() {
        val voiceStatePacket = org.openxtend.ble.IdoPacketEncoder.buildSetAlexaVoiceState(true)
        assertEquals(5, voiceStatePacket.size)
        assertEquals(0x12.toByte(), voiceStatePacket[0])
        assertEquals(0x24.toByte(), voiceStatePacket[1])
        assertEquals(0x01.toByte(), voiceStatePacket[2])

        val voiceReadyPacket = org.openxtend.ble.IdoPacketEncoder.buildSetAlexaReady()
        assertEquals(4, voiceReadyPacket.size)
        assertEquals(0x12.toByte(), voiceReadyPacket[0])
        assertEquals(0x21.toByte(), voiceReadyPacket[1])

        val frames = org.openxtend.ble.IdoPacketEncoder.buildVoiceAssistantResponse("Hello from Gemini")
        assertTrue(frames.isNotEmpty())
        assertEquals(20, frames[0].size)
        assertEquals(0x13.toByte(), frames[0][0])
        assertEquals(0x01.toByte(), frames[0][1])
    }
}
