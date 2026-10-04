package com.vdx.sonic.voice

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Shared PCM16 → WAV helper for ASR engines. */
object WavUtil {
    fun pcm16ToWav(samples: ShortArray, sampleRate: Int = 16000): ByteArray {
        val byteRate = sampleRate * 2
        val dataSize = samples.size * 2
        val fileSize = 36 + dataSize
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)
        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(fileSize))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(byteRate))
        dos.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(dataSize))
        for (sample in samples) {
            dos.writeShort(java.lang.Short.reverseBytes(sample).toInt())
        }
        return bos.toByteArray()
    }
}
