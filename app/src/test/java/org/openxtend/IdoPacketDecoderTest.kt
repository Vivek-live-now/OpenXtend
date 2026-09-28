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
}
