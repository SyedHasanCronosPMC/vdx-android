package com.vdx.call

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.vdx.R
import com.vdx.settings.KeyVault
import com.vdx.sonic.voice.GroqAsrEngine
import com.vdx.sonic.voice.PcmMicCapture
import kotlinx.coroutines.*

class CallActivity : AppCompatActivity() {
    private val flow = CallFlow(SystemClock::elapsedRealtime)
    private val scope = MainScope()
    private lateinit var speech: CallSpeech
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var choices: LinearLayout
    private lateinit var confirm: Button
    private lateinit var targetLabel: TextView
    private lateinit var speakButton: Button
    private lateinit var fallback: Button
    private var tts: TextToSpeech? = null
    private var localTts = false
    private var lookup: Job? = null
    private var revision = 0
    private var pendingName: String? = null
    private var bubblePermission = false
    private var localFailed = false
    private var cloud: PcmMicCapture? = null
    private var cloudJob: Job? = null
    private var cloudLimit: Job? = null

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (bubblePermission) prepareBubble() else listen()
        } else message("Microphone permission was not granted. You can still type a name.")
    }
    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val name = pendingName
        pendingName = null
        if (granted && name != null) resolve(name)
        else message("Contacts permission was not granted. Type a phone number instead.")
    }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bubblePermission && Settings.canDrawOverlays(this)) startBubble()
        else message("Floating microphone is off. You can still speak here.")
        bubblePermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)
        val root = findViewById<View>(R.id.call_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        status = findViewById(R.id.call_status)
        input = findViewById(R.id.call_input)
        choices = findViewById(R.id.call_choices)
        confirm = findViewById(R.id.call_confirm)
        targetLabel = findViewById(R.id.call_target)
        speakButton = findViewById(R.id.call_speak)
        fallback = findViewById(R.id.call_fallback)
        speech = CallSpeech(this)
        tts = TextToSpeech(this) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val voice = tts?.voices?.firstOrNull { !it.isNetworkConnectionRequired && it.locale.language == java.util.Locale.getDefault().language }
                if (voice != null) { tts?.voice = voice; localTts = true }
            }
        }
        speakButton.setOnClickListener {
            when {
                cloud != null -> finishCloud()
                speech.listening -> { speech.cancel(); resetMic(); message("Listening stopped.") }
                else -> { bubblePermission = false; requestMic() }
            }
        }
        findViewById<Button>(R.id.call_find).setOnClickListener { submit(input.text.toString()) }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { submit(input.text.toString()); true } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { cancelAll() }
            override fun afterTextChanged(s: Editable?) {}
        })
        confirm.setOnClickListener { openDialer() }
        findViewById<Button>(R.id.call_cancel).setOnClickListener { cancelAll(); message("Cancelled. No dialer was opened.") }
        findViewById<Button>(R.id.call_bubble).setOnClickListener {
            cancelAll()
            bubblePermission = true
            requestMic()
        }
        fallback.setOnClickListener { fallbackOptions() }
        findViewById<Button>(R.id.call_voice_settings).setOnClickListener { configureCloud() }
    }

    override fun onResume() {
        super.onResume()
        CallBubbleService.instance?.cancelListening()
        CallVoiceInbox.take()?.let { outcome ->
            if (outcome.error) voiceError(outcome.text) else submit(outcome.text)
        }
    }

    override fun onStop() {
        cancelAll()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun requestMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (bubblePermission) prepareBubble() else listen()
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun prepareBubble() {
        if (Settings.canDrawOverlays(this)) { startBubble(); bubblePermission = false; return }
        AlertDialog.Builder(this).setTitle("Floating microphone")
            .setMessage("Allow VDX to show a microphone over other apps. It listens only when tapped. This is optional; speaking here and typing work without it.")
            .setPositiveButton("Allow bubble") { _, _ ->
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }.setNegativeButton("Speak here") { _, _ -> bubblePermission = false; listen() }
            .setOnCancelListener { bubblePermission = false }.show()
    }

    private fun startBubble() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, CallBubbleService::class.java))
            message("Floating microphone ready. Tap it to speak; hold it to close.")
        } catch (_: Exception) { message("Floating microphone could not start. You can still speak here.") }
    }

    private fun listen(systemFallback: Boolean = false) {
        stopCloud()
        lookup?.cancel()
        revision++
        choices.removeAllViews()
        tts?.stop()
        if (!systemFallback) { localFailed = false; fallback.visibility = View.GONE }
        message(if (systemFallback) "Listening with your system speech service…" else "Listening on your device…", false)
        speakButton.text = "Stop listening"
        speech.start(systemFallback, result = { text -> resetMic(); submit(text) }, error = ::voiceError)
    }

    private fun voiceError(text: String) {
        localFailed = true
        resetMic()
        fallback.visibility = View.VISIBLE
        message(text)
    }

    private fun submit(raw: String) {
        speech.cancel()
        stopCloud()
        resetMic()
        if (CallFlow.isCancel(raw)) { clearRequest(); message("Cancelled. No dialer was opened."); return }
        if (flow.awaitingConfirmation && CallFlow.isYes(raw)) { openDialer(); return }
        clearRequest()
        val name = CallFlow.targetFromInput(raw)
        if (name == null) { message("Say or type a person's name or a phone number."); return }
        val number = CallFlow.normalizeNumber(name)
        if (number != null) { propose(CallTarget("number", number, number)); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingName = name
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        resolve(name)
    }

    private fun resolve(name: String) {
        val request = ++revision
        message("Looking in your contacts…", false)
        lookup = scope.launch {
            val matches = try { withContext(Dispatchers.IO) { CallContacts(this@CallActivity).find(name) } }
            catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { message("Contacts access is unavailable. Type a phone number instead."); return@launch }
            catch (e: IllegalArgumentException) { message(e.message ?: "Use a fuller contact name."); return@launch }
            catch (_: Exception) { message("Contacts could not be read. Try again or type a number."); return@launch }
            if (request != revision) return@launch
            when (matches.size) {
                0 -> message("No matching phone number. Try a fuller name or type the number.")
                1 -> propose(matches.single())
                else -> {
                    if (matches.size > 10) { message("Too many matches. Please type a fuller name."); return@launch }
                    message("Choose the person and number you mean.")
                    matches.forEach { target ->
                        choices.addView(Button(this@CallActivity).apply {
                            text = "${target.name}\n${target.number}"
                            textSize = 20f
                            minHeight = (64 * resources.displayMetrics.density).toInt()
                            setOnClickListener { propose(target) }
                        })
                    }
                }
            }
        }
    }

    private fun propose(target: CallTarget) {
        choices.removeAllViews()
        if (!flow.propose(target)) { message("This contact does not have a supported phone number."); return }
        confirm.visibility = View.VISIBLE
        targetLabel.text = "${target.name}\n${target.number}"
        targetLabel.visibility = View.VISIBLE
        message("Call ${target.name} at ${target.number}? Say yes or tap Open dialer. You will tap Call in the phone app.")
    }

    private fun openDialer() {
        speech.cancel()
        stopCloud()
        resetMic()
        val target = flow.confirm()
        confirm.visibility = View.GONE
        targetLabel.visibility = View.GONE
        if (target == null) { message("Confirmation expired. Find the person again."); return }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", target.number, null)))
            message("Dialer opened. Tap Call in the phone app to connect.", false)
        } catch (_: ActivityNotFoundException) { message("No phone dialer is installed. No call was placed.") }
        catch (_: SecurityException) { message("The phone dialer could not open. No call was placed.") }
    }

    private fun fallbackOptions() {
        if (!localFailed) return
        AlertDialog.Builder(this).setTitle("Choose a voice fallback")
            .setMessage("Your system speech service may send audio to its provider. Groq sends a new recording to Groq using your saved key. Typing sends neither.")
            .setPositiveButton("System speech") { _, _ -> listen(systemFallback = true) }
            .setNeutralButton("Use my Groq key") { _, _ -> startCloud() }
            .setNegativeButton("Keep typing", null).show()
    }

    private fun configureCloud() {
        cancelAll()
        val field = EditText(this).apply {
            hint = "Groq API key (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false
        }
        AlertDialog.Builder(this).setTitle("Optional Groq voice fallback")
            .setMessage("A key is never required. It is encrypted on this phone. After on-device voice fails, you can explicitly choose to send a new audio recording to Groq. Saving a key does not enable automatic uploads.")
            .setView(field).setPositiveButton("Save key") { _, _ ->
                val key = field.text.toString().trim()
                if (key.isNotBlank() && KeyVault.isAvailable(this)) {
                    KeyVault.put(this, KeyVault.GROQ, key)
                    message("Key saved on this phone. On-device voice remains first.")
                } else message("Key was not saved. Enter a key and ensure secure storage is available.")
                field.text.clear()
            }.setNeutralButton("Remove key") { _, _ -> KeyVault.put(this, KeyVault.GROQ, null); message("Groq key removed.") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun startCloud() {
        if (!localFailed) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            message("Allow microphone access by tapping Speak, then try again."); return
        }
        if (KeyVault.get(this, KeyVault.GROQ).isNullOrBlank()) { message("No Groq key saved. Use Optional cloud voice key, or keep typing."); return }
        speech.cancel()
        lookup?.cancel()
        revision++
        choices.removeAllViews()
        tts?.stop()
        cloud = PcmMicCapture().takeIf { it.start() }
        if (cloud == null) { message("Microphone could not start. Try typing instead."); return }
        message("Recording for Groq. Tap Stop and transcribe when finished.", false)
        speakButton.text = "Stop and transcribe"
        cloudLimit = scope.launch { delay(20_000); finishCloud() }
    }

    private fun finishCloud() {
        val capture = cloud ?: return
        cloud = null
        cloudLimit?.cancel()
        cloudLimit = null
        val audio = capture.stop()
        resetMic()
        val key = KeyVault.get(this, KeyVault.GROQ) ?: run { message("Groq key unavailable. Try typing."); return }
        val request = ++revision
        message("Transcribing with Groq…", false)
        cloudJob = scope.launch {
            try {
                val result = GroqAsrEngine(key).transcribe(audio)
                if (request != revision) return@launch
                if (result.text.isBlank() || result.confidence < 0.3f) voiceError("Groq did not return a clear name. Try typing.")
                else { cloudJob = null; submit(result.text) }
            } finally { audio.fill(0) }
        }
    }

    private fun stopCloud() {
        cloudLimit?.cancel(); cloudLimit = null
        cloud?.stop()?.fill(0); cloud = null
        cloudJob?.cancel(); cloudJob = null
    }

    private fun clearRequest() {
        revision++
        lookup?.cancel(); lookup = null
        pendingName = null
        flow.cancel()
        choices.removeAllViews()
        confirm.visibility = View.GONE
        targetLabel.text = ""
        targetLabel.visibility = View.GONE
    }

    private fun cancelAll() {
        clearRequest()
        speech.cancel()
        stopCloud()
        tts?.stop()
        resetMic()
    }

    private fun resetMic() { speakButton.setText(R.string.call_speak) }
    private fun message(text: String, aloud: Boolean = true) {
        status.text = text
        if (aloud && localTts && !speech.listening && cloud == null) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "call-status")
    }
}
