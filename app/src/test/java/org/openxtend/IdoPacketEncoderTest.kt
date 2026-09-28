package org.openxtend

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openxtend.ble.IdoPacketEncoder
import java.util.Calendar

class IdoPacketEncoderTest {

    @Test
    fun testGetDeviceInfo() {
        val packet = IdoPacketEncoder.buildGetDeviceInfo()
        assertArrayEquals(byteArrayOf(0x02, 0x01), packet)
    }

    @Test
    fun testGetBatteryInfo() {
        val packet = IdoPacketEncoder.buildGetBatteryInfo()
        assertArrayEquals(byteArrayOf(0x02, 0x05), packet)
    }

    @Test
    fun testGetLiveData() {
        val packet = IdoPacketEncoder.buildGetLiveData()
        assertArrayEquals(byteArrayOf(0x02, 0xA0.toByte()), packet)
    }

    @Test
    fun testSetTimeStructure() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MARCH, 8, 1, 11, 18)
        val packet = IdoPacketEncoder.buildSetTime(cal)

        assertEquals(16, packet.size)
        assertEquals(0x03.toByte(), packet[0])
        assertEquals(0x01.toByte(), packet[1])
        // Year 2026 = 0x07EA -> low byte 0xEA, high byte 0x07
        assertEquals(0xEA.toByte(), packet[2])
        assertEquals(0x07.toByte(), packet[3])
        // Month 3
        assertEquals(0x03.toByte(), packet[4])
        // Day 8
        assertEquals(0x08.toByte(), packet[5])
        // Hour 1
        assertEquals(0x01.toByte(), packet[6])
        // Min 11
        assertEquals(0x0B.toByte(), packet[7])
        // Sec 18
        assertEquals(0x12.toByte(), packet[8])
    }

    @Test
    fun testRaiseToWakeToggles() {
        val enablePacket = IdoPacketEncoder.buildSetRaiseToWake(true)
        val disablePacket = IdoPacketEncoder.buildSetRaiseToWake(false)

        assertEquals(9, enablePacket.size)
        assertEquals(0xAA.toByte(), enablePacket[2])

        assertEquals(9, disablePacket.size)
        assertEquals(0x55.toByte(), disablePacket[2])
    }

    @Test
    fun testMusicControlToggles() {
        val enablePacket = IdoPacketEncoder.buildSetMusicControl(true)
        val disablePacket = IdoPacketEncoder.buildSetMusicControl(false)

        assertArrayEquals(byteArrayOf(0x03, 0x2A, 0xAA.toByte(), 0x55), enablePacket)
        assertArrayEquals(byteArrayOf(0x03, 0x2A, 0x55, 0x55), disablePacket)
    }

    @Test
    fun testRebootPacket() {
        val packet = IdoPacketEncoder.buildReboot()
        assertArrayEquals(byteArrayOf(0xF0.toByte(), 0x01), packet)
    }

    @Test
    fun testNotificationChunking() {
        val frames = IdoPacketEncoder.buildNotificationPackets(
            typeId = 8, // WhatsApp
            sender = "Alice",
            message = "Hello from OpenXtend on boAt Xtend!"
        )

        assert(frames.isNotEmpty())
        for ((idx, frame) in frames.withIndex()) {
            assertEquals(20, frame.size)
            assertEquals(0x05.toByte(), frame[0])
            assertEquals(0x03.toByte(), frame[1])
            assertEquals(frames.size.toByte(), frame[2])
            assertEquals((idx + 1).toByte(), frame[3])
        }

        // First frame payload check
        assertEquals(8.toByte(), frames[0][4]) // type WhatsApp
    }
}
