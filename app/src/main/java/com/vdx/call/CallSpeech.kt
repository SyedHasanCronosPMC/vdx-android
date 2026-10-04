package com.vdx.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** One explicit utterance. Never silently switches providers or rearms the mic. */
class CallSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var timeout: Runnable? = null
    var listening = false
        private set

    fun start(systemFallback: Boolean = false, result: (String) -> Unit, error: (String) -> Unit) {
        cancel()
        val attempt = generation
        fun fail(message: String) {
            if (attempt != generation) return
            cancel()
            error(message)
        }
        try {
            recognizer = if (!systemFallback) {
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    fail("On-device voice is unavailable. Type a name, or choose a voice fallback.")
                    return
                }
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    fail("No system speech service is installed. You can still type a name.")
                    return
                }
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            listening = true
            recognizer!!.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    if (attempt != generation) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                    if (text.isBlank() || (confidence != null && confidence >= 0 && confidence < 0.3f)) {
                        fail("I did not catch that. Try again or type a name.")
                        return
                    }
                    cancel()
                    result(text)
                }
                override fun onError(code: Int) = fail(when (code) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice. Typing still works."
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear a name. Try again or type it."
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The on-device language model is unavailable. Try a voice fallback or type a name."
                    else -> "Voice recognition could not finish. Try again, use a fallback, or type a name."
                })
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            timeout = Runnable { fail("Listening timed out. Tap to try again or type a name.") }.also {
                handler.postDelayed(it, 20_000)
            }
            recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { fail("Voice could not start. Type a name or choose a fallback.") }
    }

    fun cancel() {
        generation++
        listening = false
        timeout?.let(handler::removeCallbacks)
        timeout = null
        val old = recognizer
        recognizer = null
        try { old?.cancel() } catch (_: Exception) {}
        try { old?.destroy() } catch (_: Exception) {}
    }
}
