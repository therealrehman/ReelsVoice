package com.oklabs.reelsvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class VoiceService : Service() {

    companion object {
        @Volatile var running = false
        /** MainActivity ka log dikhane ke liye */
        @Volatile var onHeard: ((String) -> Unit)? = null

        private const val CHANNEL = "rv_voice"
        private const val NOTIF_ID = 1
        private const val DEBOUNCE_MS = 300L
        private const val RATE = 16000
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var worker: Thread? = null
    private var lastFire = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        running = true
        stopped = false
        if (worker?.isAlive != true) {
            worker = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopped = true
        running = false
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice control", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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

    private fun report(s: String) {
        main.post { onHeard?.invoke(s) }
    }

    private fun makeRecognizer(m: Model, grammar: String): Recognizer =
        try {
            Recognizer(m, RATE.toFloat(), grammar)
        } catch (e: Throwable) {
            Recognizer(m, RATE.toFloat())
        }

    private fun loop() {
        val models = ArrayList<Model>()
        val recs = ArrayList<Recognizer>()
        var ar: AudioRecord? = null
        try {
            report("Model load ho raha hai…")
            for (s in ModelStore.specs) {
                if (!ModelStore.ready(this, s)) continue
                val m = Model(ModelStore.dir(this, s).absolutePath)
                models.add(m)
                recs.add(makeRecognizer(m, s.grammar))
            }
            if (recs.isEmpty()) {
                report("Model nahi mila — pehle 'Model download' karein")
                main.post { stopSelf() }
                return
            }

            val minBuf = AudioRecord.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            ar = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 16000)
            )
            ar.startRecording()
            report("Sun raha hoon ✔ (${recs.size} model)")

            val buf = ByteArray(1600) // 50 ms ka audio
            while (!stopped) {
                val n = ar.read(buf, 0, buf.size)
                if (n <= 0) continue
                var fired = false
                for (r in recs) {
                    val isFinal = r.acceptWaveForm(buf, n)
                    val text = if (isFinal) field(r.result, "text") else field(r.partialResult, "partial")
                    if (text.isEmpty()) continue
                    report((if (isFinal) "" else "… ") + text)
                    if (!fired && tryFire(text)) fired = true
                }
                if (fired) for (r in recs) r.reset()
            }
        } catch (e: Throwable) {
            report("Error: ${e.message}")
        } finally {
            try { ar?.stop() } catch (_: Throwable) {}
            try { ar?.release() } catch (_: Throwable) {}
            for (r in recs) try { r.close() } catch (_: Throwable) {}
            for (m in models) try { m.close() } catch (_: Throwable) {}
            running = false
        }
    }

    private fun field(json: String, key: String): String =
        try { JSONObject(json).optString(key, "") } catch (e: Exception) { "" }

    private fun tryFire(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastFire < DEBOUNCE_MS) return false
        val cmd = Commands.match(listOf(text)) ?: return false
        lastFire = now
        main.post {
            val ok = Commands.run(cmd)
            onHeard?.invoke(if (ok) "✔ $cmd" else "✘ $cmd — Accessibility service ON nahi hai")
        }
        return true
    }
}
