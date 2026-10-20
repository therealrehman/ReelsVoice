package com.oklabs.reelsvoice

/**
 * Sab input (buttons, voice, baad mein aur) yahin se swipe chalate hain.
 * true = gesture bhej diya gaya, false = service OFF hai ya Instagram khula nahi.
 */
object ReelActions {
    fun next(): Boolean = ScrollService.instance?.swipe(true) ?: false
    fun back(): Boolean = ScrollService.instance?.swipe(false) ?: false
    fun pause(): Boolean = ScrollService.instance?.tap(1) ?: false
    fun like(): Boolean = ScrollService.instance?.tap(2) ?: false
}
