package com.oklabs.reelsvoice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

object ReelActions {

    private val handler = Handler(Looper.getMainLooper())
    private var paused = false

    private fun svc(): AccessibilityService? = ScrollService.instance

    private fun swipe(s: AccessibilityService, fromY: Float, toY: Float) {
        val m = s.resources.displayMetrics
        val x = m.widthPixels * 0.5f
        val p = Path().apply {
            moveTo(x, m.heightPixels * fromY)
            lineTo(x, m.heightPixels * toY)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 70))
            .build()
        s.dispatchGesture(g, null, null)
    }

    private fun tap(s: AccessibilityService) {
        val m = s.resources.displayMetrics
        val p = Path().apply { moveTo(m.widthPixels * 0.5f, m.heightPixels * 0.45f) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 15))
            .build()
        s.dispatchGesture(g, null, null)
    }

    /** true = action chali, false = service ON nahi hai */
    fun next(): Boolean { val s = svc() ?: return false; paused = false; swipe(s, 0.75f, 0.25f); return true }

    fun back(): Boolean { val s = svc() ?: return false; paused = false; swipe(s, 0.25f, 0.75f); return true }

    fun like(): Boolean {
        val s = svc() ?: return false
        // double tap EK hi gesture mein (handler delay nahi): sirf like karta hai, unlike nahi
        val m = s.resources.displayMetrics
        val x = m.widthPixels * 0.5f
        val y = m.heightPixels * 0.45f
        val p1 = Path().apply { moveTo(x, y) }
        val p2 = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, 15))
            .addStroke(GestureDescription.StrokeDescription(p2, 35, 15))
            .build()
        s.dispatchGesture(g, null, null)
        return true
    }

    fun pause(): Boolean {
        val s = svc() ?: return false
        if (!paused) { tap(s); paused = true }
        return true
    }

    fun start(): Boolean {
        val s = svc() ?: return false
        if (paused) { tap(s); paused = false }
        return true
    }

    fun share(): Boolean {
        val s = svc() ?: return false
        val root = s.rootInActiveWindow ?: return true
        val node = findByDesc(root, listOf("share", "send"))
        node?.let { clickUp(it) }
        return true
    }

    /** naya reel aane par paused state reset */
    fun resetPaused() { paused = false }

    private fun findByDesc(n: AccessibilityNodeInfo, keys: List<String>): AccessibilityNodeInfo? {
        val d = n.contentDescription?.toString()?.lowercase()
        if (d != null && keys.any { d == it || d.startsWith("$it ") || d.endsWith(" $it") }) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findByDesc(c, keys)?.let { return it }
        }
        return null
    }

    private fun clickUp(n: AccessibilityNodeInfo) {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable) { cur.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
            cur = cur.parent
        }
    }
}
