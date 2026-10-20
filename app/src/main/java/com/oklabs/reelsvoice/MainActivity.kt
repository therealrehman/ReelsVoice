package com.oklabs.reelsvoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var heard: TextView
    private lateinit var voiceBtn: Button
    private lateinit var modelBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "Reels Voice (Vosk offline)"
            textSize = 24f
            setTextColor(Color.BLACK)
        })

        status = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(status)

        root.addView(btn("1. Accessibility settings kholo") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        modelBtn = btn("2. Voice models download (~85 MB, ek baar)") { downloadModels() }
        root.addView(modelBtn)

        voiceBtn = btn("3. Voice control START") { toggleVoice() }
        root.addView(voiceBtn)

        heard = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#006600"))
            setPadding(0, dp(12), 0, dp(12))
            text = "Suna hua: -"
        }
        root.addView(heard)

        root.addView(TextView(this).apply {
            text = "Test buttons (dabane ke 5 second baad chalenge, Instagram Reels khol lo):"
            setPadding(0, dp(8), 0, dp(4))
        })
        for (c in Cmd.values()) {
            root.addView(btn("Test: ${c.name.lowercase()}") { delayed(c) })
        }
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        VoiceService.onHeard = { s -> heard.text = "Suna hua: $s"; refreshUi() }
        refreshUi()
    }

    override fun onPause() {
        VoiceService.onHeard = null
        super.onPause()
    }

    private fun refreshUi() {
        val a11y = ScrollService.instance != null
        val models = ModelStore.specs.joinToString(" ") {
            it.folder + (if (ModelStore.ready(this, it)) "✔" else "✘")
        }
        status.text = "Accessibility: " + (if (a11y) "ON ✔" else "OFF ✘") +
            "\nModels: $models" +
            "\nVoice: " + (if (VoiceService.running) "sun raha hai ✔" else "band")
        voiceBtn.text = if (VoiceService.running) "3. Voice control STOP" else "3. Voice control START"
        modelBtn.isEnabled = !ModelStore.downloading && !ModelStore.allReady(this)
    }

    private fun downloadModels() {
        if (ModelStore.downloading) return
        Thread {
            ModelStore.downloadAll(applicationContext) { msg ->
                main.post { heard.text = msg; refreshUi() }
            }
            main.post { refreshUi() }
        }.start()
        main.postDelayed({ refreshUi() }, 300)
    }

    private fun toggleVoice() {
        if (VoiceService.running) {
            stopService(Intent(this, VoiceService::class.java))
            main.postDelayed({ refreshUi() }, 400)
            return
        }
        if (!ModelStore.anyReady(this)) {
            Toast.makeText(this, "Pehle models download karein", Toast.LENGTH_LONG).show()
            return
        }
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 10) else startVoice()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        } else {
            Toast.makeText(this, "Mic permission ke baghair voice nahi chalegi", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoice() {
        startForegroundService(Intent(this, VoiceService::class.java))
        main.postDelayed({ refreshUi() }, 500)
    }

    private fun delayed(c: Cmd) {
        Toast.makeText(this, "5 second mein ${c.name.lowercase()}…", Toast.LENGTH_SHORT).show()
        main.postDelayed({
            if (!Commands.run(c))
                Toast.makeText(this, "Accessibility service ON nahi hai", Toast.LENGTH_LONG).show()
        }, 5000)
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick(); refreshUi() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
