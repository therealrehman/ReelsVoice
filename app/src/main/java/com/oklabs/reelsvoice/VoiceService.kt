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
        /** MainActivity ko sunai diya text dikhane ke liye */
        @Volatile var onHeard: ((String) -> Unit)? = null
        /** mic level 0..100 */
        @Volatile var level = 0
        @Volatile var micName = "-"

        private const val CHANNEL = "rv_voice"
        private const val NOTIF_ID = 1
        private const val DEBOUNCE_MS = 300L
        private const val RATE = 16000
        private val SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )
        private val NAMES = arrayOf("VOICE_RECOGNITION", "MIC", "VOICE_COMMUNICATION")
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var worker: Thread? = null
    private var lastFire = 0L
    @Volatile private var audio: AudioRecord? = null
    private var srcIdx = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForegroundCompat()
        } catch (e: Throwable) {
            AppLog.add("Foreground service start FAIL: ${e.message}")
            report("Service start fail: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        stopped = false
        if (worker?.isAlive != true) {
            worker = Thread { loop() }.also { it.start() }
        }
        return START_NOT_STICKY
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

    private fun status(s: String) {
        AppLog.add(s)
        report(s)
    }

    private fun makeRecognizer(m: Model, s: ModelStore.Spec): Recognizer =
        try {
            Recognizer(m, RATE.toFloat(), s.grammar)
        } catch (e: Throwable) {
            AppLog.add("${s.folder}: grammar fail (${e.message}), bina grammar chalega")
            Recognizer(m, RATE.toFloat())
        }

    private fun openAudio(from: Int): Boolean {
        closeAudio()
        val minBuf = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val size = maxOf(minBuf * 2, RATE * 2)
        for (i in from until SOURCES.size) {
            try {
                val a = AudioRecord(
                    SOURCES[i], RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, size
                )
                if (a.state != AudioRecord.STATE_INITIALIZED) {
                    AppLog.add("Mic ${NAMES[i]}: init fail")
                    a.release()
                    continue
                }
                a.startRecording()
                if (a.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    AppLog.add("Mic ${NAMES[i]}: recording shuru nahi hui")
                    a.release()
                    continue
                }
                audio = a
                srcIdx = i
                micName = NAMES[i]
                AppLog.add("Mic ON: ${NAMES[i]}")
                return true
            } catch (e: Throwable) {
                AppLog.add("Mic ${NAMES[i]} error: ${e.message}")
            }
        }
        return false
    }

    private fun closeAudio() {
        val a = audio ?: return
        audio = null
        try { a.stop() } catch (e: Throwable) {}
        try { a.release() } catch (e: Throwable) {}
    }

    private fun loop() {
        val models = ArrayList<Model>()
        val recs = ArrayList<Recognizer>()
        val names = ArrayList<String>()
        try {
            status("Model load ho raha hai…")
            for (s in ModelStore.specs) {
                if (!ModelStore.ready(this, s)) {
                    AppLog.add("Model ${s.folder}: tayyar nahi, skip")
                    continue
                }
                try {
                    val m = Model(ModelStore.dir(this, s).absolutePath)
                    models.add(m)
                    recs.add(makeRecognizer(m, s))
                    names.add(s.folder)
                    AppLog.add("Model ${s.folder}: load OK")
                } catch (e: Throwable) {
                    AppLog.add("Model ${s.folder} load FAIL: ${e.message}")
                    ModelStore.reset(this, s)
                }
            }
            if (recs.isEmpty()) {
                status("Koi model load nahi hua — 'Models dobara download' dabayein")
                main.post { stopSelf() }
                return
            }
            if (!openAudio(0)) {
                status("Mic nahi khula — neeche log dekhein")
                main.post { stopSelf() }
                return
            }
            status("Sun raha hoon ✔ (${recs.size} model, mic $micName)")

            val buf = ByteArray(1600) // 50 ms ka audio
            var silent = 0
            var errs = 0
            var switches = 0
            while (!stopped) {
                val a = audio ?: break
                val n = a.read(buf, 0, buf.size)
                if (n <= 0) {
                    errs++
                    if (errs == 1 || errs % 100 == 0) AppLog.add("Mic read error code $n")
                    if (errs > 60) {
                        AppLog.add("Mic dobara khol raha hoon")
                        if (!openAudio(srcIdx)) break
                        errs = 0
                    }
                    try { Thread.sleep(20) } catch (e: InterruptedException) {}
                    continue
                }
                errs = 0

                var peak = 0
                var i = 0
                while (i + 1 < n) {
                    val v = (buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)
                    val av = if (v < 0) -v else v
                    if (av > peak) peak = av
                    i += 2
                }
                level = minOf(100, peak / 100)

                if (peak == 0) silent++ else silent = 0
                if (silent >= 60) { // 3 second pura zero = OS ne mic block kiya
                    silent = 0
                    if (switches < 4) {
                        switches++
                        AppLog.add("Mic se sirf khamoshi (zero) — dusra source try")
                        val next = if (srcIdx + 1 < SOURCES.size) srcIdx + 1 else 0
                        if (!openAudio(next)) break
                    } else if (switches == 4) {
                        switches++
                        status("Mic sirf zero de raha hai — koi aur app mic use kar rahi hai ya OS block kar raha hai")
                    }
                }

                var fired = false
                for (idx in recs.indices) {
                    val r = recs[idx]
                    val isFinal = r.acceptWaveForm(buf, n)
                    val text = if (isFinal) field(r.result, "text") else field(r.partialResult, "partial")
                    if (text.isEmpty()) continue
                    if (isFinal) AppLog.add("suna[${names[idx]}]: $text")
                    report((if (isFinal) "" else "… ") + text)
                    if (!fired && tryFire(text)) fired = true
                }
                if (fired) for (r in recs) r.reset()
            }
        } catch (e: Throwable) {
            status("Error: ${e.message}")
        } finally {
            closeAudio()
            for (r in recs) try { r.close() } catch (e: Throwable) {}
            for (m in models) try { m.close() } catch (e: Throwable) {}
            running = false
            level = 0
            micName = "-"
            AppLog.add("Voice band")
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
            val msg = if (ok) "✔ $cmd" else "✘ $cmd — Accessibility service ON nahi hai"
            AppLog.add(msg)
            onHeard?.invoke(msg)
        }
        return true
    }
}
