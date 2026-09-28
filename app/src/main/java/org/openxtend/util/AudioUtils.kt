package org.openxtend.util

import java.io.ByteArrayOutputStream

object AudioUtils {

    /**
     * Wraps raw PCM audio bytes into a standard 44-byte RIFF/WAVE header container.
     */
    fun wrapPcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val out = ByteArrayOutputStream(totalAudioLen + 44)
        // RIFF header
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(intToBytesLE(totalDataLen))
        out.write("WAVE".toByteArray(Charsets.US_ASCII))

        // Subchunk 1: "fmt "
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(intToBytesLE(16)) // subchunk1size (16 for PCM)
        out.write(shortToBytesLE(1)) // audio format 1 = PCM
        out.write(shortToBytesLE(channels.toInt()))
        out.write(intToBytesLE(sampleRate))
        out.write(intToBytesLE(byteRate))
        out.write(shortToBytesLE((channels * bitsPerSample / 8))) // block align
        out.write(shortToBytesLE(bitsPerSample.toInt()))

        // Subchunk 2: "data"
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(intToBytesLE(totalAudioLen))
        out.write(pcmData)

        return out.toByteArray()
    }

    private fun intToBytesLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytesLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
}
