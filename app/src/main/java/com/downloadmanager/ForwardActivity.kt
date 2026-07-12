package com.downloadmanager

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForwardActivity : Activity() {

    companion object {
        private val HISTORY_LOCK = Any()

        private data class Dm(
            val key: String, val label: String,
            val pkg: String, val cls: String?
        )

        private val DMS = arrayOf(
            Dm("idm_plus", "IDM+", "idm.internet.download.manager.plus",
                "idm.internet.download.manager.UrlHandlerDownloader"),
            Dm("idm", "IDM", "idm.internet.download.manager",
                "idm.internet.download.manager.UrlHandlerDownloader"),
            Dm("adm_pro", "ADM Pro", "com.dv.adm.pay", null),
            Dm("adm", "ADM", "com.dv.adm", null),
            Dm("fdm", "FDM", "org.freedownloadmanager.fdm", null),
            Dm("one_dm", "1DM+", "nextapp.downloader",
                "nextapp.downloader.activities.HandleLinkActivity")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: ""
        if (url.isBlank()) { finish(); return }

        val filename = intent.getStringExtra("filename")
        val mime = intent.getStringExtra("mime")
        val referer = intent.getStringExtra("referer")
        val cookies = intent.getStringExtra("cookies")
        val ua = intent.getStringExtra("user_agent")

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val selected = prefs.getString(MainActivity.KEY_DM, "auto") ?: "auto"

        logHistory(url, filename, mime, referer, "pending")

        val (launched, label) = route(selected, url, filename, mime, referer, cookies, ua)

        if (!launched) {
            Toast.makeText(this,
                "⚠️ No download manager found — install IDM+, ADM, or 1DM+",
                Toast.LENGTH_LONG).show()
        }

        updateHistory(url, if (launched) label else "No DM")
        finish()
        overridePendingTransition(0, 0)
    }

    private fun route(
        selected: String, url: String, filename: String?, mime: String?,
        referer: String?, cookies: String?, ua: String?
    ): Pair<Boolean, String> {
        val pm = packageManager
        val installed = DMS.filter { pkg(it.pkg, pm) }
        if (installed.isEmpty()) return Pair(false, "")

        // Selected DM first, then others as fallback
        val queue = mutableListOf<Dm>()
        if (selected != "auto") {
            installed.find { it.key == selected }?.let { queue.add(it) }
        }
        queue.addAll(installed.filter { it !in queue })

        for (dm in queue) {
            if (launch(dm, url, filename, mime, referer, cookies, ua)) {
                return Pair(true, dm.label)
            }
        }
        return Pair(false, "")
    }

    private fun launch(
        dm: Dm, url: String, filename: String?, mime: String?,
        referer: String?, cookies: String?, ua: String?
    ): Boolean {
        // Try explicit component first if available
        if (dm.cls != null) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    component = ComponentName(dm.pkg, dm.cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("url", url)
                    if (!filename.isNullOrBlank()) putExtra("filename", filename)
                    if (!mime.isNullOrBlank()) putExtra("mime", mime)
                    putHeaders(this, referer, cookies, ua)
                }
                startActivity(i)
                return true
            } catch (_: Exception) {}
        }
        // Fallback: ACTION_VIEW with package
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(dm.pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putHeaders(this, referer, cookies, ua)
            }
            startActivity(i)
            true
        } catch (_: Exception) { false }
    }

    private fun putHeaders(i: Intent, referer: String?, cookies: String?, ua: String?) {
        if (!referer.isNullOrBlank()) {
            i.putExtra("referer", referer)
            i.putExtra("referrer", referer)
        }
        if (!cookies.isNullOrBlank()) {
            i.putExtra("cookies", cookies)
            i.putExtra("cookie", cookies)
        }
        if (!ua.isNullOrBlank()) {
            i.putExtra("user_agent", ua)
            i.putExtra("useragent", ua)
        }
    }

    private fun pkg(p: String, pm: android.content.pm.PackageManager): Boolean {
        return try { pm.getPackageInfo(p, 0); true } catch (_: Exception) { false }
    }

    private fun logHistory(url: String, fn: String?, mime: String?, ref: String?, dm: String) {
        Thread {
            synchronized(HISTORY_LOCK) {
                try {
                    val t = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val line = "time=${e(t)}|filename=${e(fn ?: "")}|mime=${e(mime ?: "")}|url=${e(url)}|referer=${e(ref ?: "")}|dm=${e(dm)}"
                    File(filesDir, "history.txt").appendText(line + "\n")
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun updateHistory(url: String, label: String) {
        Thread {
            synchronized(HISTORY_LOCK) {
                try {
                    val f = File(filesDir, "history.txt")
                    if (!f.exists()) return@synchronized
                    val eu = e(url)
                    val lines = f.readLines().map { line ->
                        if (line.contains("url=$eu") && line.contains("dm=pending"))
                            line.replace("dm=pending", "dm=${e(label)}")
                        else line
                    }
                    f.writeText(lines.joinToString("\n") + "\n")
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun e(v: String): String =
        v.replace("%", "%25").replace("|", "%7C").replace("=", "%3D")
            .replace("\n", "%0A").replace("\r", "%0D").replace("\t", "%09")
}
