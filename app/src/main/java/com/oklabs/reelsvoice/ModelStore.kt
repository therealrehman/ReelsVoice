package com.oklabs.reelsvoice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object ModelStore {

    data class Spec(val folder: String, val url: String, val grammar: String)

    val specs = listOf(
        Spec(
            "en",
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            """["next","back","previous","like","pause","stop","start","play","resume","share","send","[unk]"]"""
        ),
        Spec(
            "hi",
            "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            """["अगला","अगली","आगे","नेक्स्ट","पीछे","पिछला","वापस","बैक","लाइक","पसंद","रुको","रोको","रुक","पॉज","चलो","चलाओ","शुरू","प्ले","स्टार्ट","शेयर","भेजो","[unk]"]"""
        )
    )

    @Volatile var downloading = false

    fun dir(ctx: Context, s: Spec) = File(ctx.filesDir, "models/${s.folder}")

    /** model folder mein asli model files maujood hain? */
    fun valid(ctx: Context, s: Spec): Boolean {
        val d = dir(ctx, s)
        return File(d, "am").isDirectory && File(d, "conf").isDirectory && File(d, "graph").isDirectory
    }

    fun ready(ctx: Context, s: Spec) = File(dir(ctx, s), ".done").exists() && valid(ctx, s)

    fun anyReady(ctx: Context) = specs.any { ready(ctx, it) }

    fun allReady(ctx: Context) = specs.all { ready(ctx, it) }

    fun reset(ctx: Context, s: Spec) {
        dir(ctx, s).deleteRecursively()
    }

    fun resetAll(ctx: Context) {
        for (s in specs) reset(ctx, s)
        AppLog.add("Models delete kar diye")
    }

    /** Blocking. Background thread se chalayein. */
    fun downloadAll(ctx: Context, progress: (String) -> Unit): Boolean {
        downloading = true
        try {
            for (s in specs) {
                if (ready(ctx, s)) continue
                var ok = false
                var lastErr = ""
                for (attempt in 1..3) {
                    try {
                        fetchOne(ctx, s) { progress(it) }
                        ok = true
                        break
                    } catch (e: Exception) {
                        lastErr = e.message ?: e.javaClass.simpleName
                        AppLog.add("${s.folder} download try $attempt fail: $lastErr")
                        progress("${s.folder}: try $attempt fail ($lastErr)")
                        reset(ctx, s)
                        try { Thread.sleep(1500) } catch (e2: InterruptedException) {}
                    }
                }
                if (!ok) {
                    progress("Download fail (${s.folder}): $lastErr")
                    return false
                }
            }
            AppLog.add("Models tayyar")
            progress("Models tayyar ✔")
            return true
        } finally {
            downloading = false
        }
    }

    private fun fetchOne(ctx: Context, s: Spec, progress: (String) -> Unit) {
        val root = File(ctx.filesDir, "models").also { it.mkdirs() }
        val zip = File(root, "${s.folder}.zip")
        zip.delete()
        AppLog.add("${s.folder}: download shuru")
        download(s.url, zip) { pct -> progress("${s.folder} model download: $pct%") }
        progress("${s.folder} model unzip ho raha hai…")
        val out = dir(ctx, s)
        out.deleteRecursively()
        out.mkdirs()
        val count = unzipStrip(zip, out)
        zip.delete()
        if (count == 0 || !valid(ctx, s)) throw IOException("zip mein model files nahi mili")
        File(out, ".done").writeText("ok")
        AppLog.add("${s.folder}: model tayyar ($count files)")
    }

    private fun open(url: String): HttpURLConnection {
        var u = url
        for (i in 0 until 6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 20000
            c.readTimeout = 30000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ReelsVoice")
            c.connect()
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirect bina Location")
                u = URL(URL(u), loc).toString()
                c.disconnect()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            return c
        }
        throw IOException("bohat zyada redirect")
    }

    private fun download(url: String, dest: File, onPct: (Int) -> Unit) {
        val c = open(url)
        val total = c.contentLengthLong
        var done = 0L
        var last = -1
        c.inputStream.use { inp ->
            FileOutputStream(dest).use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != last) { last = pct; onPct(pct) }
                    }
                }
            }
        }
        c.disconnect()
        if (total > 0 && done != total) throw IOException("adhoori download ($done/$total)")
        if (done < 1_000_000) throw IOException("file bohat chhoti ($done bytes)")
    }

    /** zip ka pehla folder naam hata kar nikalta hai. Nikalay hue files ki ginti lautata hai. */
    private fun unzipStrip(zip: File, outDir: File): Int {
        val base = outDir.canonicalPath
        var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name.substringAfter('/', "")
                if (name.isEmpty()) continue
                val f = File(outDir, name)
                if (!f.canonicalPath.startsWith(base)) continue
                if (e.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { o -> z.copyTo(o) }
                    count++
                }
            }
        }
        return count
    }
}
