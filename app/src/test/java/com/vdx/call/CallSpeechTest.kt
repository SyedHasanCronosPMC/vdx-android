package com.vdx.call

import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
class CallSpeechTest {
    @Test fun missingOnDeviceServiceFailsWithoutCreatingGenericRecognizer() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(false)
        val speech = CallSpeech(ApplicationProvider.getApplicationContext())
        var failure = ""
        speech.start(result = { fail("Unexpected transcript") }, error = { failure = it })
        assertTrue(failure.contains("On-device"))
        assertFalse(speech.listening)
        assertNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun cancelledSessionAndOldCallbacksCannotProduceResults() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val speech = CallSpeech(ApplicationProvider.getApplicationContext())
        var results = 0
        speech.start(result = { results++ }, error = { fail(it) })
        shadowOf(Looper.getMainLooper()).idle()
        val old = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.cancel()
        old.triggerOnResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("call 911")) })
        assertEquals(0, results)
        assertTrue(old.isDestroyed)
        assertFalse(speech.listening)
    }

    @Test fun successfulUtteranceReturnsOnceAndClosesMicrophone() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        val speech = CallSpeech(ApplicationProvider.getApplicationContext())
        val received = mutableListOf<String>()
        speech.start(result = received::add, error = { fail(it) })
        shadowOf(Looper.getMainLooper()).idle()
        val recognizer = shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        val result = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("Mom")) }
        recognizer.triggerOnResults(result)
        recognizer.triggerOnResults(result)
        assertEquals(listOf("Mom"), received)
        assertTrue(recognizer.isDestroyed)
    }
}
