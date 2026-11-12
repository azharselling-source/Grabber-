package com.azhar.grabber

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0 Mobile Safari/537.36"
        val HEIGHTS = listOf(0, 1080, 720, 480)
        val IMAGE_URL = Regex("""\.(jpg|jpeg|png|webp|gif)(\?.*)?$""", RegexOption.IGNORE_CASE)
    }

    private lateinit var urlBox: EditText
    private lateinit var goBtn: TextView
    private lateinit var updBtn: TextView
    private lateinit var bar: NeonProgress
    private lateinit var status: TextView
    private lateinit var historyBox: LinearLayout
    private lateinit var qualityBlock: LinearLayout
    private lateinit var typeChips: List<TextView>
    private lateinit var qualChips: List<TextView>

    private var selType = 0   // 0 video, 1 audio, 2 photo
    private var selQual = 0
    private var busy = false

    private val prefs by lazy { getSharedPreferences("grabber", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG
        setContentView(buildUi())
        showHistory()
        handleIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Engine.init(applicationContext)
                runOnUiThread { status.text = "Tayyar hai. Link paste karo." }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Engine shuru nahi hua: ${shortError(e)}" }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // App chhor kar jao (home dabao ya doosri app kholo) to link wala dabba khali ho jata hai,
    // taake agli baar app khulne par purana link na dikhe.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) urlBox.setText("")
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            extractUrl(i.getStringExtra(Intent.EXTRA_TEXT))?.let { urlBox.setText(it) }
        }
    }

    private fun extractUrl(text: String?): String? =
        text?.let { Regex("https?://\\S+").find(it)?.value }

    // ------------------------------------------------------------ styling
    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun neon(radiusDp: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Theme.CYAN, Theme.VIOLET)
        ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun lp(width: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun medium() = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun caption(t: String) = TextView(this).apply {
        text = t
        textSize = 11f
        letterSpacing = 0.18f
        setTextColor(Theme.MUTED)
        typeface = medium()
        setPadding(dp(4), dp(22), 0, dp(8))
    }

    private fun chip(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = medium()
        setPadding(dp(8), dp(12), dp(8), dp(12))
    }

    private fun styleChip(v: TextView, selected: Boolean) {
        if (selected) {
            v.background = neon(12)
            v.setTextColor(Theme.ON_ACCENT)
        } else {
            v.background = rounded(Theme.CARD, 12, Theme.STROKE)
            v.setTextColor(Theme.MUTED)
        }
    }

    private fun chipRow(labels: List<String>, onPick: (Int) -> Unit): Pair<LinearLayout, List<TextView>> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = labels.mapIndexed { i, l ->
            chip(l).also { c ->
                val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                p.setMargins(dp(3), 0, dp(3), 0)
                row.addView(c, p)
                c.setOnClickListener { onPick(i) }
            }
        }
        return row to chips
    }

    private fun smallAction(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        letterSpacing = 0.08f
        typeface = medium()
        setTextColor(Theme.CYAN)
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(Theme.BG, 10, Theme.STROKE)
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- UI
    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(36))
            setBackgroundColor(Theme.BG)
        }

        // header
        root.addView(TextView(this).apply {
            text = "GRABBER"
            textSize = 34f
            letterSpacing = 0.14f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Theme.CYAN)
            paint.shader = LinearGradient(
                0f, 0f, dp(190).toFloat(), 0f, Theme.CYAN, Theme.VIOLET, Shader.TileMode.CLAMP
            )
        }, lp())
        root.addView(TextView(this).apply {
            text = "Video  •  Audio  •  Photo, kisi bhi link se"
            textSize = 13f
            setTextColor(Theme.MUTED)
            setPadding(dp(2), dp(4), 0, dp(22))
        }, lp())

        // link card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.CARD, 20, Theme.STROKE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        urlBox = EditText(this).apply {
            hint = "Link yahan paste karo"
            setHintTextColor(Theme.MUTED)
            setTextColor(Theme.TEXT)
            textSize = 15f
            background = null
            minLines = 2
            maxLines = 4
            setPadding(0, dp(4), 0, dp(4))
        }
        card.addView(urlBox, lp())
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        actions.addView(smallAction("PASTE") { pasteFromClipboard() }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        actions.addView(smallAction("CLEAR") { urlBox.setText("") }, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8)
        })
        card.addView(actions, lp())
        root.addView(card, lp())

        // type chips
        root.addView(caption("KYA CHAHIYE"), lp())
        val (typeRow, tChips) = chipRow(listOf("▶  Video", "♫  Audio", "▣  Photo")) { i ->
            selType = i
            refreshChips()
        }
        typeChips = tChips
        root.addView(typeRow, lp())

        // quality chips (video only)
        qualityBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        qualityBlock.addView(caption("QUALITY"), lp())
        val (qualRow, qChips) = chipRow(listOf("Best", "1080p", "720p", "480p")) { i ->
            selQual = i
            refreshChips()
        }
        qualChips = qChips
        qualityBlock.addView(qualRow, lp())
        root.addView(qualityBlock, lp())
        refreshChips()

        // download button
        goBtn = TextView(this).apply {
            text = "DOWNLOAD"
            textSize = 17f
            letterSpacing = 0.2f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Theme.ON_ACCENT)
            gravity = Gravity.CENTER
            background = neon(16)
            setPadding(0, dp(17), 0, dp(17))
            setOnClickListener { startDownload() }
        }
        root.addView(goBtn, lp().apply { topMargin = dp(26) })

        // progress + status
        bar = NeonProgress(this).apply { visibility = View.INVISIBLE }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
            topMargin = dp(16)
        })
        status = TextView(this).apply {
            text = "Engine tayyar ho raha hai..."
            textSize = 13f
            setTextColor(Theme.MUTED)
            setPadding(dp(2), dp(10), 0, dp(10))
        }
        root.addView(status, lp())

        updBtn = smallAction("⟳  ENGINE UPDATE (agar download na chale)") { updateEngine() }
        root.addView(updBtn, lp(ViewGroup.LayoutParams.WRAP_CONTENT))

        // history
        root.addView(caption("RECENT"), lp())
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyBox, lp())

        root.addView(TextView(this).apply {
            text = "Files Movies / Music / Pictures ke Grabber folder mein save hoti hain. " +
                "Download hote waqt app khuli rakho. Sirf apne personal use ke liye, " +
                "aur doosron ke haqooq ka khayal rakho."
            textSize = 11f
            setTextColor(Theme.MUTED)
            setPadding(dp(2), dp(26), 0, 0)
        }, lp())

        return ScrollView(this).apply {
            setBackgroundColor(Theme.BG)
            isFillViewport = true
            addView(root)
        }
    }

    private fun refreshChips() {
        typeChips.forEachIndexed { i, c -> styleChip(c, i == selType) }
        qualChips.forEachIndexed { i, c -> styleChip(c, i == selQual) }
        qualityBlock.visibility = if (selType == 0) View.VISIBLE else View.GONE
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        val url = extractUrl(text)
        if (url == null) toast("Clipboard mein koi link nahi mila") else urlBox.setText(url)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun setBusy(b: Boolean) {
        busy = b
        goBtn.isEnabled = !b
        goBtn.alpha = if (b) 0.45f else 1f
        updBtn.isEnabled = !b
        updBtn.alpha = if (b) 0.45f else 1f
        if (!b) {
            bar.indeterminate = false
            bar.visibility = View.INVISIBLE
        }
    }

    private fun showBar() {
        bar.visibility = View.VISIBLE
        bar.fraction = 0f
        bar.indeterminate = true
    }

    // ------------------------------------------------------------ download
    private fun startDownload() {
        val url = extractUrl(urlBox.text.toString())
        if (url == null) {
            toast("Pehle sahi link paste karo")
            return
        }
        if (busy) return
        setBusy(true)
        showBar()
        status.text = "Download shuru ho raha hai..."

        val type = selType
        val height = HEIGHTS[selQual]

        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(cacheDir, "dl/${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                Engine.init(applicationContext)

                if (type == 2) {
                    runOnUiThread { status.text = "Picture dhoond raha hoon..." }
                    downloadPicture(url, dir)
                } else {
                    val req = YoutubeDLRequest(url)
                    req.addOption("--no-playlist")
                    req.addOption("--no-mtime")
                    req.addOption("-o", dir.absolutePath + "/%(title).80s.%(ext)s")
                    if (type == 1) {
                        req.addOption("-x")
                        req.addOption("--audio-format", "mp3")
                        req.addOption("--audio-quality", "0")
                    } else {
                        req.addOption("-f", "bv*+ba/b")
                        req.addOption(
                            "-S",
                            if (height > 0) "res:$height,vcodec:h264,acodec:m4a"
                            else "res,vcodec:h264,acodec:m4a"
                        )
                        req.addOption("--merge-output-format", "mp4")
                    }
                    YoutubeDL.getInstance().execute(req, null) { progress, _, _ ->
                        runOnUiThread {
                            if (progress >= 0f) {
                                bar.indeterminate = false
                                bar.fraction = progress / 100f
                                status.text = "Download ho raha hai: ${progress.toInt()}%"
                            }
                        }
                    }
                }

                val files = readyFiles(dir)
                if (files.isEmpty()) throw IOException("Koi file nahi bani")

                runOnUiThread { status.text = "Phone mein save ho raha hai..." }
                val saved = files.map { f -> saveToPhone(f, type).also { f.delete() } }
                addHistory(files.first().nameWithoutExtension, type)
                val more = if (saved.size > 1) " (+${saved.size - 1} aur)" else ""
                runOnUiThread { status.text = "Ho gaya! Save hui: ${saved.first()}$more" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Masla aaya: ${shortError(e)}" }
            } finally {
                dir.deleteRecursively()
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun readyFiles(dir: File): List<File> =
        dir.listFiles()?.filter {
            it.isFile && it.length() > 0 && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl")
        }.orEmpty()

    // ------------------------------------------------------------- pictures
    private fun downloadPicture(url: String, dir: File) {
        // 1) direct link to an image file
        if (IMAGE_URL.containsMatchIn(url)) {
            fetchImage(url, dir)
            return
        }
        // 2) yt-dlp: photo posts / carousel images / cover image of a video post
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--skip-download")
            req.addOption("--ignore-no-formats-error")
            req.addOption("--write-thumbnail")
            req.addOption("--convert-thumbnails", "jpg")
            req.addOption("--playlist-end", "10")
            req.addOption("-o", dir.absolutePath + "/%(title).60s_%(autonumber)s.%(ext)s")
            YoutubeDL.getInstance().execute(req, null) { _, _, _ -> }
        } catch (ignored: Exception) {
            // fall through to the page-scrape fallback
        }
        if (readyFiles(dir).isNotEmpty()) return
        // 3) last resort: the page's own preview image (og:image)
        val img = findOgImage(url)
            ?: throw IOException("Is link se picture nahi mili. Private ya login wali post ho sakti hai.")
        fetchImage(img, dir)
    }

    private fun findOgImage(pageUrl: String): String? {
        val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", UA)
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val html = conn.inputStream.bufferedReader().use { r ->
                val buf = CharArray(512 * 1024)
                val n = r.read(buf)
                if (n > 0) String(buf, 0, n) else ""
            }
            val a = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            val b = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)
            (a.find(html) ?: b.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")
        } catch (ignored: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchImage(imgUrl: String, dir: File) {
        val conn = (URL(imgUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Server ne ${conn.responseCode} diya")
            val type = conn.contentType.orEmpty()
            val ext = when {
                "png" in type -> "png"
                "webp" in type -> "webp"
                "gif" in type -> "gif"
                else -> "jpg"
            }
            val last = Uri.parse(imgUrl).lastPathSegment.orEmpty().substringBeforeLast('.')
            val clean = last.replace(Regex("[^\\w-]"), "_").takeLast(40)
            val base = if (clean.isBlank()) "picture_${System.currentTimeMillis()}" else clean
            val out = File(dir, "$base.$ext")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            if (out.length() == 0L) throw IOException("Khali file mili")
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------- saving
    private fun saveToPhone(f: File, type: Int): String {
        val ext = f.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (type) {
                1 -> "audio/mpeg"
                2 -> "image/jpeg"
                else -> "video/mp4"
            }
        val (collection, folder) = when {
            mime.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            mime.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mime.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Grabber")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values) ?: throw IOException("File save nahi hui")
        contentResolver.openOutputStream(uri)?.use { out ->
            f.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("File save nahi hui")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return "$folder/Grabber/${f.name}"
    }

    // ------------------------------------------------------------ update
    private fun updateEngine() {
        if (busy) return
        setBusy(true)
        showBar()
        status.text = "Engine update ho raha hai..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Engine.init(applicationContext)
                val result = YoutubeDL.getInstance().updateYoutubeDL(applicationContext)
                runOnUiThread { status.text = "Update mukammal: $result" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Update nahi hua: ${shortError(e)}" }
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    // ----------------------------------------------------------- history
    private fun addHistory(title: String, type: Int) {
        val old = prefs.getString("history2", "").orEmpty().lines().filter { it.isNotBlank() }
        val updated = (listOf("$type|$title") + old).take(10)
        prefs.edit().putString("history2", updated.joinToString("\n")).apply()
        runOnUiThread { showHistory() }
    }

    private fun showHistory() {
        historyBox.removeAllViews()
        val rows = prefs.getString("history2", "").orEmpty().lines().filter { it.contains('|') }
        if (rows.isEmpty()) {
            historyBox.addView(TextView(this).apply {
                text = "Abhi koi download nahi"
                textSize = 13f
                setTextColor(Theme.MUTED)
                setPadding(dp(4), 0, 0, 0)
            }, lp())
            return
        }
        rows.forEach { row ->
            val t = row.substringBefore('|').toIntOrNull() ?: 0
            val title = row.substringAfter('|')
            val icon = when (t) {
                1 -> "♫"
                2 -> "▣"
                else -> "▶"
            }
            historyBox.addView(TextView(this).apply {
                text = "$icon   $title"
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(Theme.TEXT)
                background = rounded(Theme.CARD, 14, Theme.STROKE)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }, lp().apply { bottomMargin = dp(8) })
        }
    }

    private fun shortError(e: Throwable): String {
        val m = (e.message ?: e.toString()).trim()
        return if (m.length > 350) "..." + m.takeLast(350) else m
    }
}
