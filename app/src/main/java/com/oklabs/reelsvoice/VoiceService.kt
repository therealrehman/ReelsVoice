package com.oklabs.reelsvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceService : Service() {

    companion object {
        @Volatile var running = false
        /** MainActivity ka log dikhane ke liye */
        @Volatile var onHeard: ((String) -> Unit)? = null

        const val PREFS = "rv"
        const val KEY_LANG = "lang"
        val LANGS = listOf("en-IN", "hi-IN", "ur-PK")
        private const val CHANNEL = "rv_voice"
        private const val NOTIF_ID = 1
        private const val DEBOUNCE_MS = 200L
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var lastFire = 0L
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        running = true
        stopped = false
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            report("Speech recognizer nahi mila (Google app / Speech Services chahiye)")
            stopSelf()
            return START_NOT_STICKY
        }
        main.post { restartNow() }
        return START_STICKY
    }

    override fun onDestroy() {
        stopped = true
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Voice control", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Reels Voice sun raha hai")
            .setContentText("next / back / like / pause / start / share")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun lang(): String {
        val i = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_LANG, 0)
        return LANGS[i.coerceIn(0, LANGS.size - 1)]
    }

    private var listenIntent: Intent? = null

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100L)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }

    /** pehli baar ya error ke baad: naya recognizer */
    private fun restartNow() {
        if (stopped) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        listenIntent = buildIntent()
        recognizer?.startListening(listenIntent)
    }

    /** tez: wahi recognizer dobara use karo (create/destroy ka delay nahi) */
    private fun listenAgain() {
        if (stopped) return
        val r = recognizer
        if (r == null) { restartNow(); return }
        try { r.startListening(listenIntent ?: buildIntent()) } catch (e: Exception) { restartNow() }
    }

    private fun restartLater(ms: Long) {
        if (stopped) return
        main.postDelayed({ restartNow() }, ms)
    }

    private fun report(s: String) {
        main.post { onHeard?.invoke(s) }
    }

    private fun handle(list: List<String>, partial: Boolean): Boolean {
        if (list.isEmpty()) return false
        report((if (partial) "… " else "") + list.first())
        val now = System.currentTimeMillis()
        if (now - lastFire < DEBOUNCE_MS) return false
        val cmd = Commands.match(list) ?: return false
        lastFire = now
        val ok = Commands.run(cmd)
        report(if (ok) "✔ $cmd" else "✘ $cmd — Accessibility service ON nahi hai")
        return true
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val l = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
            if (handle(l, true)) {
                // command mil gayi: final result ka intezar nahi, foran dobara sunna shuru
                recognizer?.cancel()
                main.post { listenAgain() }
            }
        }

        override fun onResults(results: Bundle?) {
            val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (l != null) handle(l, false)
            main.post { listenAgain() }
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    report("Mic permission nahi hai")
                    stopSelf()
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> main.post { listenAgain() }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartLater(500)
                else -> restartLater(500)
            }
        }
    }
}
