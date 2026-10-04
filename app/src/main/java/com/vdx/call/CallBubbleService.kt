package com.vdx.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import com.vdx.R

/** Transient in-process handoff: exported launch intents cannot inject transcripts. */
internal object CallVoiceInbox {
    data class Outcome(val text: String, val error: Boolean)
    private var outcome: Outcome? = null
    fun put(text: String, error: Boolean) { outcome = Outcome(text, error) }
    fun take(): Outcome? = outcome.also { outcome = null }
}

/** Explicitly enabled foreground microphone, with no ambient/wake-word capture. */
class CallBubbleService : Service() {
    companion object {
        var instance: CallBubbleService? = null
            private set
        private const val CHANNEL = "call-microphone"
        private const val STOP = "com.vdx.call.STOP"
    }
    private lateinit var speech: CallSpeech
    private var bubble: Button? = null
    private lateinit var windows: WindowManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        speech = CallSpeech(this)
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            getString(R.string.call_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, CallActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CallBubbleService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        startForeground(41, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(getString(R.string.call_app_name))
            .setContentText(getString(R.string.call_notification)).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.call_stop_bubble), stop).build())
            .setOngoing(true).build())
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val size = (72 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (12 * resources.displayMetrics.density).toInt()
        }
        bubble = Button(this).apply {
            text = "Mic"
            contentDescription = "Tap to say a name. Hold to close microphone."
            setOnClickListener {
                if (speech.listening) { cancelListening(); return@setOnClickListener }
                text = "Stop"
                contentDescription = "Stop listening"
                speech.start(result = { showResult(it, false) }, error = { showResult(it, true) })
            }
            setOnLongClickListener { stopSelf(); true }
        }
        try { windows.addView(bubble, params) }
        catch (_: Exception) { stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) stopSelf()
        return START_NOT_STICKY
    }

    fun cancelListening() {
        speech.cancel()
        bubble?.text = "Mic"
        bubble?.contentDescription = "Tap to say a name. Hold to close microphone."
    }

    private fun showResult(text: String, error: Boolean) {
        cancelListening()
        CallVoiceInbox.put(text, error)
        try {
            startActivity(Intent(this, CallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (_: Exception) {
            CallVoiceInbox.take()
            android.widget.Toast.makeText(this, "Open VDX to continue.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        cancelListening()
        bubble?.let { try { windows.removeView(it) } catch (_: Exception) {} }
        bubble = null
        instance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
