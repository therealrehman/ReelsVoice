package com.oklabs.reelsvoice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
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

    fun ready(ctx: Context, s: Spec) = File(dir(ctx, s), ".done").exists()

    fun anyReady(ctx: Context) = specs.any { ready(ctx, it) }

    fun allReady(ctx: Context) = specs.all { ready(ctx, it) }

    /** Blocking. Background thread se chalayein. */
    fun downloadAll(ctx: Context, progress: (String) -> Unit): Boolean {
        downloading = true
        try {
            for (s in specs) {
                if (ready(ctx, s)) continue
                val root = File(ctx.filesDir, "models").also { it.mkdirs() }
                val zip = File(root, "${s.folder}.zip")
                download(s.url, zip) { pct -> progress("${s.folder} model download: $pct%") }
                progress("${s.folder} model unzip ho raha hai…")
                val out = dir(ctx, s)
                out.deleteRecursively()
                out.mkdirs()
                unzipStrip(zip, out)
                zip.delete()
                File(out, ".done").writeText("ok")
            }
            progress("Models tayyar ✔")
            return true
        } catch (e: Exception) {
            progress("Download fail: ${e.message}")
            return false
        } finally {
            downloading = false
        }
    }

    private fun download(url: String, dest: File, onPct: (Int) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.connect()
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
    }

    /** zip ka pehla folder naam hata kar nikalta hai, isliye folder ka naam kuch bhi ho chalega */
    private fun unzipStrip(zip: File, outDir: File) {
        val base = outDir.canonicalPath
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
                }
            }
        }
    }
}
