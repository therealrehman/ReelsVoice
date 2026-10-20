package com.oklabs.reelsvoice

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        status = TextView(this).apply { textSize = 18f }
        root.addView(status)

        root.addView(button("1. Accessibility settings kholo") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Allow restricted settings (App info)") {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        })

        root.addView(TextView(this).apply {
            text = "2. Test: button dabayein, phir 5 second ke andar Instagram Reels kholein."
            setPadding(0, pad, 0, pad / 2)
        })

        root.addView(button("NEXT (5 sec baad)") { delayed { ReelActions.next() } })
        root.addView(button("BACK (5 sec baad)") { delayed { ReelActions.back() } })
        root.addView(button("LIKE - double tap (5 sec baad)") { delayed { ReelActions.like() } })
        root.addView(button("PAUSE / PLAY (5 sec baad)") { delayed { ReelActions.pause() } })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ScrollService.instance != null) {
            "Service: ON ✅"
        } else {
            "Service: OFF ❌\nAccessibility mein \"Reels Voice\" ko ON karein."
        }
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setAllCaps(false)
            setOnClickListener { onClick() }
        }

    private fun delayed(action: () -> Boolean) {
        Toast.makeText(this, "5 second mein Instagram Reels kholein", Toast.LENGTH_SHORT).show()
        handler.postDelayed({
            val ok = action()
            val msg = if (ok) "Gesture bhej diya" else "Nahi hua: service OFF hai ya Instagram khula nahi"
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }, 5000L)
    }
}
