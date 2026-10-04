package com.vdx.sonic.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroqAudioContractTest {
    @Test fun wavHasValidLittleEndianPcmHeaderAndLosslessSamples() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768, 1234)
        val wav = WavUtil.pcm16ToWav(samples)
        val bytes = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(wav.size - 8, bytes.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1, bytes.getShort(20).toInt())
        assertEquals(1, bytes.getShort(22).toInt())
        assertEquals(16000, bytes.getInt(24))
        assertEquals(32000, bytes.getInt(28))
        assertEquals(2, bytes.getShort(32).toInt())
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals(samples.size * 2, bytes.getInt(40))
        samples.forEachIndexed { i, sample -> assertEquals(sample, bytes.getShort(44 + i * 2)) }
    }
    @Test fun documentedWhisperMetadataProducesUsableQualityScore() {
        val result = GroqAsrEngine("unused").parseResponse("""{"text":"Mom","language":"english","segments":[{"text":"Mom","start":0,"end":1,"avg_logprob":-0.2,"no_speech_prob":0.01}]}""")
        assertEquals("Mom", result.text)
        assertTrue(result.confidence > 0.3f)
    }
    @Test fun silenceAndMissingQualityMetadataFailClosed() {
        val engine = GroqAsrEngine("unused")
        assertEquals(0f, engine.parseResponse("""{"text":"Mom","segments":[{"text":"Mom","avg_logprob":-0.1,"no_speech_prob":0.99}]}""").confidence)
        assertEquals(0f, engine.parseResponse("""{"text":"Mom","segments":[{"text":"Mom"}]}""").confidence)
    }
}
