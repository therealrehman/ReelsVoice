package com.oklabs.reelsvoice

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Chhota in-memory log: screen par dikhta hai aur "Log copy" se share ho sakta hai. */
object AppLog {
    private const val MAX = 150
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(msg: String) {
        lines.addLast(fmt.format(Date()) + "  " + msg)
        while (lines.size > MAX) lines.removeFirst()
        Log.i("ReelsVoice", msg)
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")
}
