package com.oklabs.reelsvoice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Screen par swipe / tap chalata hai, lekin sirf tab jab Instagram khula ho.
 * Voice (Step 2) isi service ko ReelActions ke zariye command dega.
 */
class ScrollService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Is step mein events ki zaroorat nahi.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    private fun isInstagramOpen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString()
        if (Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
            root.recycle()
        }
        return pkg == INSTAGRAM
    }

    /** up = true: ungli neeche se upar (agli reel). up = false: upar se neeche (pichli reel). */
    fun swipe(up: Boolean): Boolean {
        if (!isInstagramOpen()) return false
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val startY = m.heightPixels * (if (up) 0.75f else 0.25f)
        val endY = m.heightPixels * (if (up) 0.25f else 0.75f)
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 150L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** count = 1: pause/play. count = 2: like (double tap). */
    fun tap(count: Int): Boolean {
        if (!isInstagramOpen()) return false
        val m = resources.displayMetrics
        val builder = GestureDescription.Builder()
        for (i in 0 until count) {
            val p = Path().apply { moveTo(m.widthPixels / 2f, m.heightPixels * 0.45f) }
            builder.addStroke(GestureDescription.StrokeDescription(p, i * 120L, 40L))
        }
        return dispatchGesture(builder.build(), null, null)
    }

    companion object {
        const val INSTAGRAM = "com.instagram.android"

        @Volatile
        var instance: ScrollService? = null
    }
}
