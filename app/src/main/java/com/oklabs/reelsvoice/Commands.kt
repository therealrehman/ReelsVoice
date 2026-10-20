package com.oklabs.reelsvoice

enum class Cmd { NEXT, BACK, LIKE, PAUSE, START, SHARE }

object Commands {

    private val words: Map<Cmd, Set<String>> = mapOf(
        Cmd.NEXT to setOf(
            "next", "agla", "agli", "aage", "aagay", "ag", "nex", "neks",
            "اگلا", "اگلی", "آگے", "نیکسٹ",
            "अगला", "अगली", "आगे", "नेक्स्ट"
        ),
        Cmd.BACK to setOf(
            "back", "previous", "peeche", "pichla", "pichli", "wapas", "bak",
            "پیچھے", "پچھلا", "پچھلی", "واپس", "بیک",
            "पीछे", "पिछला", "पिछली", "वापस", "बैक"
        ),
        Cmd.LIKE to setOf(
            "like", "laik", "lyk", "pasand", "heart",
            "لائک", "لائیک", "پسند",
            "लाइक", "पसंद"
        ),
        Cmd.PAUSE to setOf(
            "pause", "ruko", "ruk", "rukjao", "roko", "stop", "paws",
            "رکو", "روکو", "رک", "پاز",
            "रुको", "रोको", "रुक", "पॉज़", "पॉज"
        ),
        Cmd.START to setOf(
            "start", "play", "resume", "shuru", "chalao", "chalo", "chala",
            "چلاؤ", "چلو", "شروع", "پلے", "اسٹارٹ",
            "चलाओ", "चलो", "शुरू", "प्ले", "स्टार्ट"
        ),
        Cmd.SHARE to setOf(
            "share", "shayer", "bhejo", "send",
            "شیئر", "بھیجو",
            "शेयर", "भेजो"
        )
    )

    fun match(heard: List<String>): Cmd? {
        for (h in heard) {
            val tokens = h.lowercase()
                .replace(Regex("[.,!?؟،।]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
            for (t in tokens) {
                for ((cmd, set) in words) {
                    if (t in set) return cmd
                }
            }
        }
        return null
    }

    fun run(cmd: Cmd): Boolean = when (cmd) {
        Cmd.NEXT -> ReelActions.next()
        Cmd.BACK -> ReelActions.back()
        Cmd.LIKE -> ReelActions.like()
        Cmd.PAUSE -> ReelActions.pause()
        Cmd.START -> ReelActions.start()
        Cmd.SHARE -> ReelActions.share()
    }
}
